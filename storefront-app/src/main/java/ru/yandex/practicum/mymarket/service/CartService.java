package ru.yandex.practicum.mymarket.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.api.PaymentApi;
import ru.yandex.practicum.mymarket.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.dto.CartDetailedResponse;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CartService {

    public static final String PLUS = "PLUS";
    public static final String MINUS = "MINUS";
    public static final String DELETE = "DELETE";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PaymentApi paymentApi;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       PaymentApi paymentApi, ReactiveRedisTemplate<String, Object> redisTemplate,
                       ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.paymentApi = paymentApi;
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
    }

    @Transactional
    public Mono<CartDto> getCartDto(String cartId) {
        if (cartId == null || cartId.isEmpty()) {
            return Mono.just(new CartDto(List.of(), 0L));
        }

        return getOrCreateCart(cartId)
                .flatMap(cart -> cartItemRepository.findByCartId(cartId).collectList())
                .flatMap(this::processCartItems);
    }

    private Mono<Cart> getOrCreateCart(String cartId) {
        return cartRepository.findById(cartId)
                .switchIfEmpty(Mono.defer(() -> {
                    Cart newCart = new Cart();
                    newCart.setId(cartId);
                    return cartRepository.save(newCart);
                }));
    }

    private Mono<CartDto> processCartItems(List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            return Mono.just(new CartDto(List.of(), 0L));
        }

        Map<Long, Integer> quantityMap = cartItems.stream()
                .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity));

        List<Long> productIds = new ArrayList<>(quantityMap.keySet());
        List<String> cacheKeys = productIds.stream().map(id -> "product:" + id).toList();

        return redisTemplate.opsForValue().multiGet(cacheKeys)
                .flatMap(cachedObjects -> handleCacheResult(cachedObjects, productIds, quantityMap));
    }

    private Mono<CartDto> handleCacheResult(List<Object> cachedObjects, List<Long> productIds,
                                            Map<Long, Integer> quantityMap) {
        Map<Long, Product> finalProducts = new HashMap<>();
        List<Long> missingIds = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            Product p = (Product) cachedObjects.get(i);
            if (p != null) {
                finalProducts.put(productIds.get(i), p);
            } else {
                missingIds.add(productIds.get(i));
            }
        }

        return fetchAndCacheMissingProducts(missingIds)
                .map(dbProducts -> {
                    dbProducts.forEach(p -> finalProducts.put(p.getId(), p));
                    return buildCartDto(finalProducts, quantityMap);
                });
    }

    private Mono<List<Product>> fetchAndCacheMissingProducts(List<Long> missingIds) {
        if (missingIds.isEmpty()) {
            return Mono.just(List.of());
        }

        return productRepository.findAllById(missingIds).collectList()
                .flatMap(dbProducts -> {
                    if (dbProducts.isEmpty()) {
                        return Mono.just(dbProducts);
                    }

                    Map<String, Object> toCache = dbProducts.stream()
                            .collect(Collectors.toMap(p -> "product:" + p.getId(), p -> p));

                    return redisTemplate.opsForValue().multiSet(toCache)
                            .thenReturn(dbProducts);
                });
    }

    private CartDto buildCartDto(Map<Long, Product> finalProducts, Map<Long, Integer> quantityMap) {
        List<ItemDto> items = finalProducts.values().stream()
                .map(p -> new ItemDto(
                        p.getId(),
                        p.getTitle(),
                        p.getDescription(),
                        p.getImgPath(),
                        p.getPrice(),
                        quantityMap.get(p.getId())
                )).toList();

        long total = items.stream()
                .mapToLong(i -> i.getPrice() * i.getCount())
                .sum();

        return new CartDto(items, total);
    }

    @Transactional
    public Mono<Void> changeItemQuantity(Long id, String action, String cartId) {

        return cartItemRepository.findByCartIdAndProductId(cartId, id)
                .flatMap(cartItem -> {
                    int quantity = cartItem.getQuantity();
                    if (quantity == 1 && MINUS.equals(action) || DELETE.equals(action)) {
                        return cartItemRepository.delete(cartItem);
                    }
                    else if (quantity > 1 && MINUS.equals(action)) {
                        cartItem.setQuantity(quantity - 1);
                        return cartItemRepository.save(cartItem);
                    }
                    else if (quantity < Integer.MAX_VALUE && PLUS.equals(action)) {
                        cartItem.setQuantity(quantity + 1);
                        return cartItemRepository.save(cartItem);
                    }

                    return Mono.empty();
                })
                .then();
    }

    public Mono<CartDetailedResponse> getCartDetailed(String cartId) {
        Mono<CartDto> cartMono = getCartDto(cartId);

        Mono<Long> balanceMono = paymentApi.getBalance()
                .map(BalanceResponse::getAmount)
                .onErrorReturn(-1L);

        return Mono.zip(cartMono, balanceMono)
                .map(tuple -> {
                    CartDto cart = tuple.getT1();
                    Long balance = tuple.getT2();

                    String error = null;
                    boolean canOrder = true;

                    if (balance == -1) {
                        error = "Сервис платежей недоступен";
                        canOrder = false;
                    } else if (balance < cart.getTotal()) {
                        error = "Недостаточно средств";
                        canOrder = false;
                    }
                    return new CartDetailedResponse(cart, balance, canOrder, error);
                });
    }
}
