package ru.yandex.practicum.mymarket.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.entity.Cart;
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

    public static final String PLUS = "PLUS";
    public static final String MINUS = "MINUS";
    public static final int TTL_PRODUCT = 3;
    public static final String PRICE = "PRICE";
    public static final int TTL_PRICE = 1;
    public static final int TTL_TITLE_DESC = 3;

    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public ItemsService(ProductRepository productRepository, CartRepository cartRepository,
                        CartItemRepository cartItemRepository, ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public Mono<ItemsWithPaging> getItemsWithPaging(String search, String sort, int pageNumber, int pageSize,
                                                    String cartId) {
        String searchPattern = (search == null) ? "" : search;
        long offset = (long) (Math.max(TTL_PRICE, pageNumber) - TTL_PRICE) * pageSize;

        String pageKey = String.format("page:s:%s:sort:%s:p:%d:sz:%d", searchPattern, sort, pageNumber, pageSize);

        return Mono.zip(
                getCachedProductIds(pageKey, searchPattern, sort, pageSize, offset),

                productRepository.countByTitleAndDescription(searchPattern)
        ).flatMap(tuple -> {
            List<Long> productIds = tuple.getT1();
            long totalItems = tuple.getT2();

            return getProductsByIds(productIds)
                    .flatMap(products -> enrichWithQuantity(products, cartId)
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
        return redisTemplate.opsForList().range(key, 0, -TTL_PRICE)
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
                                    ? Duration.ofMinutes(TTL_PRICE)
                                    : Duration.ofMinutes(TTL_TITLE_DESC);

                            Object[] idsArray = ids.stream().map(id -> (Object) id).toArray();

                            return redisTemplate.opsForList().rightPushAll(key, idsArray)
                                    .then(redisTemplate.expire(key, cacheTtl))
                                    .thenReturn(ids);
                        })
                        : Mono.just(list));
    }

    private Mono<List<ItemDto>> enrichWithQuantity(List<Product> products, String cartId) {
        if (cartId == null || products.isEmpty()) {
            return Mono.just(products.stream()
                    .map(p -> new ItemDto(p.getId(), p.getTitle(), p.getDescription(), p.getImgPath(),
                            p.getPrice(), 0))
                    .toList());
        }

        List<Long> ids = products.stream().map(Product::getId).toList();
        return cartItemRepository.findAllByCartIdAndProductIds(cartId, ids)
                .collectMap(CartItem::getProductId, CartItem::getQuantity)
                .map(quantities -> products.stream()
                        .map(p -> new ItemDto(
                                p.getId(), p.getTitle(), p.getDescription(), p.getImgPath(), p.getPrice(),
                                quantities.getOrDefault(p.getId(), 0)
                        )).toList());
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
                .flatMap(key -> redisTemplate.expire(key, Duration.ofMinutes(TTL_PRODUCT)))
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

    @Transactional
    public Mono<Void> changeItemsCount(Long id, String action, ServerHttpResponse response, String cartId) {
        return productRepository.findById(id)
                .flatMap(product -> {
                    // Если cartId нет и действие PLUS — создаем новую корзину
                    if (cartId == null || cartId.isEmpty()) {
                        if (PLUS.equals(action)) {
                            String newCartId = UUID.randomUUID().toString();
                            addCartCookie(response, newCartId);
                            return createCartAndItem(id, newCartId);
                        }
                        return Mono.empty();
                    }

                    return cartRepository.findById(cartId)
                            // Если кука есть, но корзины в БД нет (например, удалена)
                            .switchIfEmpty(Mono.defer(() -> {
                                if (PLUS.equals(action)) {
                                    return createCartAndItem(id, cartId).then(Mono.empty());
                                }
                                return Mono.empty();
                            }))
                            .flatMap(cart -> setCartItem(id, action, cartId));


                });
    }

    private void addCartCookie(ServerHttpResponse response, String cartId) {
        ResponseCookie cookie = ResponseCookie.from("cartId", cartId)
                .maxAge(Duration.ofDays(7))
                .path("/")
                .httpOnly(true)
                .build();
        response.addCookie(cookie);
    }

    private Mono<Void> createCartAndItem(Long id, String cartId) {
        Cart cart = new Cart();
        cart.setId(cartId);

        return cartRepository.save(cart)
                .then(Mono.defer(() -> {
                    CartItem item = new CartItem();
                    item.setCartId(cartId);
                    item.setProductId(id);
                    item.setQuantity(TTL_PRICE);
                    // version и id не трогаем, они останутся null
                    return cartItemRepository.save(item); }))
                .then();
    }

    private Mono<Void> setCartItem(Long id, String action, String cartId) {
        return cartItemRepository.findByCartIdAndProductId(cartId, id)
                .switchIfEmpty(Mono.defer(() -> {
                    if (PLUS.equals(action)) {
                        return cartItemRepository.save(new CartItem(null, cartId, id, TTL_PRICE, null))
                                .then(Mono.empty());
                    }
                    return Mono.empty();
                }))
                .flatMap(cartItem -> {
                    int quantity = cartItem.getQuantity();

                    if (quantity == TTL_PRICE && MINUS.equals(action)) {
                        return cartItemRepository.delete(cartItem);
                    }

                    CartItem updatedItem = null;
                    if (quantity > TTL_PRICE && MINUS.equals(action)) {
                        cartItem.setQuantity(quantity - TTL_PRICE);
                        updatedItem = cartItem;
                    } else if (quantity < Integer.MAX_VALUE && PLUS.equals(action)) {
                        cartItem.setQuantity(quantity + TTL_PRICE);
                        updatedItem = cartItem;
                    }

                    return updatedItem != null ? cartItemRepository.save(updatedItem) : Mono.empty();
                })
                .then();
    }

    @Transactional(readOnly = true)
    public Mono<ItemDto> getItemWithQuantity(Long id, String cartId) {
        if (id == null) {
            return Mono.just(new ItemDto());
        }

        return getProductWithCache(id)
                .flatMap(product -> enrichWithCartQuantity(product, cartId))
                .defaultIfEmpty(new ItemDto());
    }

    private Mono<Product> getProductWithCache(Long id) {
        String cacheKey = "product:" + id;

        return redisTemplate.opsForValue().get(cacheKey)
                .cast(Product.class)
                .switchIfEmpty(Mono.defer(() ->
                        productRepository.findById(id)
                                .flatMap(product ->
                                        redisTemplate.opsForValue().set(cacheKey, product, Duration.ofMinutes(TTL_TITLE_DESC))
                                                .thenReturn(product)
                                )
                ));
    }

    private Mono<ItemDto> enrichWithCartQuantity(Product product, String cartId) {
        if (cartId == null || cartId.isEmpty()) {
            return Mono.just(mapToItemDto(product, 0));
        }

        return cartItemRepository.findByCartIdAndProductId(cartId, product.getId())
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
