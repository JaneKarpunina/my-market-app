package ru.yandex.practicum.mymarket.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ItemsService {

    public static final int PRODUCT_CACHE_TTL_MINUTES = 3;
    public static final String PRICE = "PRICE";
    public static final int PAGE_PRICE_CACHE_TTL_MINUTES = 1;
    public static final int PAGE_TITLE_DESC_CACHE_TTL_MINUTES = 3;
    private static final int TOTAL_PAGES_RANGE_END = -1;

    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final CartRepository cartRepository;

    public ItemsService(ProductRepository productRepository,
                        CartItemRepository cartItemRepository, ReactiveRedisTemplate<String, Object> redisTemplate,
                        CartRepository cartRepository) {
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.redisTemplate = redisTemplate;
        this.cartRepository = cartRepository;
    }

    @Transactional(readOnly = true)
    public Mono<ItemsWithPaging> getItemsWithPaging(String search, String sort, int pageNumber, int pageSize,
                                                    Long userId) {
        String searchPattern = (search == null) ? "" : search;
        long offset = (long) (Math.max(1, pageNumber) - 1) * pageSize;

        String pageKey = String.format("page:s:%s:sort:%s:p:%d:sz:%d", searchPattern, sort, pageNumber, pageSize);

        return Mono.zip(
                getCachedProductIds(pageKey, searchPattern, sort, pageSize, offset),

                productRepository.countByTitleAndDescription(searchPattern)
        ).flatMap(tuple -> {
            List<Long> productIds = tuple.getT1();
            long totalItems = tuple.getT2();

            return getProductsByIds(productIds)
                    .flatMap(products -> enrichWithQuantity(products, userId)
                            .map(itemDtos -> {
                                int totalPages = (int) Math.ceil((double) totalItems / pageSize);
                                int currentPage = Math.max(1,
                                        Math.min(pageNumber, totalPages == 0 ? 1 : totalPages));

                                List<List<ItemDto>> itemRows = partitionAndFill(itemDtos, 3);
                                Paging paging = new Paging(pageSize, currentPage, currentPage > 1,
                                        currentPage < totalPages);

                                return new ItemsWithPaging(itemRows, paging);
                            }));
        });
    }

    private Mono<List<Long>> getCachedProductIds(String key, String search, String sort, int limit, long offset) {
        return redisTemplate.opsForList().range(key, 0, TOTAL_PAGES_RANGE_END)
                .map(obj -> {
                    if (obj instanceof Number number) {
                        return number.longValue();
                    }
                    return Long.parseLong(obj.toString());
                })
                .collectList()
                .flatMap(list -> list.isEmpty()
                        ? productRepository.findIdsOnly(search, sort, limit, offset)
                        .collectList()
                        .flatMap(ids -> {
                            if (ids.isEmpty()) {
                                return Mono.just(ids);
                            }

                            Duration cacheTtl = PRICE.equalsIgnoreCase(sort)
                                    ? Duration.ofMinutes(PAGE_PRICE_CACHE_TTL_MINUTES)
                                    : Duration.ofMinutes(PAGE_TITLE_DESC_CACHE_TTL_MINUTES);

                            Object[] idsArray = ids.stream().map(id -> (Object) id).toArray();

                            return redisTemplate.opsForList().rightPushAll(key, idsArray)
                                    .then(redisTemplate.expire(key, cacheTtl))
                                    .thenReturn(ids);
                        })
                        : Mono.just(list));
    }

    private Mono<List<ItemDto>> enrichWithQuantity(List<Product> products, Long userId) {
        if (userId == null || products.isEmpty()) {
            return Mono.just(products.stream()
                    .map(p -> new ItemDto(p.getId(), p.getTitle(), p.getDescription(), p.getImgPath(),
                            p.getPrice(), 0))
                    .toList());
        }

        List<Long> ids = products.stream().map(Product::getId).toList();

        return cartRepository.findByUserId(userId)
                .flatMap(cart -> cartItemRepository.findAllByCartIdAndProductIds(cart.getId(), ids)
                        .collectMap(CartItem::getProductId, CartItem::getQuantity)
                        .map(quantities -> products.stream()
                                .map(p -> new ItemDto(
                                        p.getId(), p.getTitle(), p.getDescription(), p.getImgPath(), p.getPrice(),
                                        quantities.getOrDefault(p.getId(), 0)
                                )).toList())
                )
                .defaultIfEmpty(products.stream()
                        .map(p -> new ItemDto(p.getId(), p.getTitle(), p.getDescription(), p.getImgPath(),
                                p.getPrice(), 0))
                        .toList());
    }

    private Mono<List<Product>> getProductsByIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Mono.just(List.of());
        }

        List<String> cacheKeys = productIds.stream()
                .map(id -> "product:" + id)
                .toList();

        return redisTemplate.opsForValue().multiGet(cacheKeys)
                .flatMap(cachedObjects -> handleCacheResult(cachedObjects, productIds));
    }

    private Mono<List<Product>> handleCacheResult(List<Object> cachedObjects, List<Long> productIds) {
        Map<Long, Product> foundInCache = new HashMap<>();
        List<Long> missingIds = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            Product p = (Product) cachedObjects.get(i);
            if (p != null) {
                foundInCache.put(productIds.get(i), p);
            } else {
                missingIds.add(productIds.get(i));
            }
        }

        if (missingIds.isEmpty()) {
            return Mono.just(sortByIdList(foundInCache, productIds));
        }

        return fetchAndCacheMissingProducts(missingIds)
                .map(dbProducts -> {
                    dbProducts.forEach(p -> foundInCache.put(p.getId(), p));
                    return sortByIdList(foundInCache, productIds);
                });
    }

    private Mono<List<Product>> fetchAndCacheMissingProducts(List<Long> missingIds) {
        return productRepository.findAllById(missingIds).collectList()
                .flatMap(dbProducts -> {
                    if (dbProducts.isEmpty()) {
                        return Mono.just(dbProducts);
                    }

                    Map<String, Object> toCache = dbProducts.stream()
                            .collect(Collectors.toMap(p -> "product:" + p.getId(), p -> p));

                    return redisTemplate.opsForValue().multiSet(toCache)
                            .then(Mono.defer(() -> applyTtlToKeys(toCache.keySet())))
                            .thenReturn(dbProducts);
                });
    }

    private Mono<Void> applyTtlToKeys(Set<String> keys) {
        return Flux.fromIterable(keys)
                .flatMap(key -> redisTemplate.expire(key, Duration.ofMinutes(PRODUCT_CACHE_TTL_MINUTES)))
                .then();
    }



    private List<Product> sortByIdList(Map<Long, Product> productMap, List<Long> ids) {
        return ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<List<ItemDto>> partitionAndFill(List<ItemDto> list, int size) {
        List<List<ItemDto>> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            int end = Math.min(i + size, list.size());
            List<ItemDto> subList = new ArrayList<>(list.subList(i, end));

            // Дополняем "пустыми" объектами для верстки
            while (subList.size() < size) {
                ItemDto placeholder = new ItemDto();
                placeholder.setId(-1L);
                subList.add(placeholder);
            }
            rows.add(subList);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Mono<ItemDto> getItemWithQuantity(Long id, Long userId) {
        if (id == null) {
            return Mono.just(new ItemDto());
        }

        return getProductWithCache(id)
                .flatMap(product -> enrichWithCartQuantity(product, userId))
                .defaultIfEmpty(new ItemDto());
    }

    private Mono<Product> getProductWithCache(Long id) {
        String cacheKey = "product:" + id;

        return redisTemplate.opsForValue().get(cacheKey)
                .cast(Product.class)
                .switchIfEmpty(Mono.defer(() ->
                        productRepository.findById(id)
                                .flatMap(product ->
                                        redisTemplate.opsForValue().set(cacheKey, product,
                                                        Duration.ofMinutes(PRODUCT_CACHE_TTL_MINUTES))
                                                .thenReturn(product)
                                )
                ));
    }

    private Mono<ItemDto> enrichWithCartQuantity(Product product, Long userId) {
        if (userId == null) {
            return Mono.just(mapToItemDto(product, 0));
        }

        return cartRepository.findByUserId(userId)
                .flatMap(cart -> cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()))
                .map(cartItem -> mapToItemDto(product, cartItem.getQuantity()))
                .defaultIfEmpty(mapToItemDto(product, 0));
    }

    private ItemDto mapToItemDto(Product product, int quantity) {
        return new ItemDto(
                product.getId(),
                product.getTitle(),
                product.getDescription(),
                product.getImgPath(),
                product.getPrice(),
                quantity
        );
    }

}
