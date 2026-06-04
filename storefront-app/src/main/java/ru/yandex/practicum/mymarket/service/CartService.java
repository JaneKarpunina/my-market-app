package ru.yandex.practicum.mymarket.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.api.PaymentApi;
import ru.yandex.practicum.mymarket.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.dto.CartDetailedResponse;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.enums.CartAction;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static ru.yandex.practicum.mymarket.enums.CartAction.*;

@Service
public class CartService {

    public static final int PRODUCT_CACHE_TTL_MINUTES = 3;

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
    public Mono<CartDto> getCartDto(Long userId) { // Изменено на Long userId
        if (userId == null) {
            return Mono.just(new CartDto(List.of(), 0L));
        }

        return getOrCreateCart(userId)
                .flatMap(cart -> cartItemRepository.findByCartId(cart.getId()).collectList())
                .flatMap(this::processCartItems);
    }

    private Mono<Cart> getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    Cart newCart = new Cart();
                    newCart.setUserId(userId);
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
                            .then(Mono.defer(() -> Flux.fromIterable(toCache.keySet())
                                    .flatMap(key -> redisTemplate
                                            .expire(key, Duration.ofMinutes(PRODUCT_CACHE_TTL_MINUTES)))
                                    .then()))
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
    public Mono<Void> changeItemQuantity(Long id, String action, Long userId) {

        CartAction cartAction = CartAction.valueOf(action);

        return cartRepository.findByUserId(userId)
                .flatMap(cart -> cartItemRepository.findByCartIdAndProductId(cart.getId(), id))
                .flatMap(cartItem -> {
                    int quantity = cartItem.getQuantity();
                    if (quantity == 1 && MINUS == cartAction || DELETE == cartAction) {
                        return cartItemRepository.delete(cartItem);
                    }
                    else if (quantity > 1 && MINUS == cartAction) {
                        cartItem.setQuantity(quantity - 1);
                        return cartItemRepository.save(cartItem);
                    }
                    else if (quantity < Integer.MAX_VALUE && PLUS == cartAction) {
                        cartItem.setQuantity(quantity + 1);
                        return cartItemRepository.save(cartItem);
                    }

                    return Mono.empty();
                })
                .then();
    }


//    @Transactional
//    public Mono<Void> changeItemQuantity(Long id, String action, String cartId) {
//
//        CartAction cartAction = CartAction.valueOf(action);
//
//        return cartItemRepository.findByCartIdAndProductId(cartId, id)
//                .flatMap(cartItem -> {
//                    int quantity = cartItem.getQuantity();
//                    if (quantity == 1 && MINUS == cartAction || DELETE == cartAction) {
//                        return cartItemRepository.delete(cartItem);
//                    }
//                    else if (quantity > 1 && MINUS == cartAction) {
//                        cartItem.setQuantity(quantity - 1);
//                        return cartItemRepository.save(cartItem);
//                    }
//                    else if (quantity < Integer.MAX_VALUE && PLUS == cartAction) {
//                        cartItem.setQuantity(quantity + 1);
//                        return cartItemRepository.save(cartItem);
//                    }
//
//                    return Mono.empty();
//                })
//                .then();
//    }

    public Mono<CartDetailedResponse> getCartDetailed(Long userId) {
        Mono<CartDto> cartMono = getCartDto(userId);

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

//    public Mono<CartDetailedResponse> getCartDetailed(String cartId) {
//        Mono<CartDto> cartMono = getCartDto(cartId);
//
//        Mono<Long> balanceMono = paymentApi.getBalance()
//                .map(BalanceResponse::getAmount)
//                .onErrorReturn(-1L);
//
//        return Mono.zip(cartMono, balanceMono)
//                .map(tuple -> {
//                    CartDto cart = tuple.getT1();
//                    Long balance = tuple.getT2();
//
//                    String error = null;
//                    boolean canOrder = true;
//
//                    if (balance == -1) {
//                        error = "Сервис платежей недоступен";
//                        canOrder = false;
//                    } else if (balance < cart.getTotal()) {
//                        error = "Недостаточно средств";
//                        canOrder = false;
//                    }
//                    return new CartDetailedResponse(cart, balance, canOrder, error);
//                });
//    }

    @Transactional
    public Mono<Void> changeItemsCount(Long id, String action, Long userId) {
        CartAction cartAction = CartAction.valueOf(action);

        return productRepository.findById(id)
                .flatMap(product -> cartRepository.findByUserId(userId)
                        // Если корзины у пользователя нет, создаем новую
                        .switchIfEmpty(Mono.defer(() -> {
                            if (PLUS == cartAction) {
                                return createCartAndItem(id, userId).then(Mono.empty());
                            }
                            return Mono.empty();
                        }))
                        .flatMap(cart -> setCartItem(id, action, cart.getId()))
                );
    }

    private Mono<Void> createCartAndItem(Long id, Long userId) {
        Cart cart = new Cart();
        cart.setUserId(userId);

        return cartRepository.save(cart)
                .flatMap(savedCart -> {
                    CartItem item = new CartItem();
                    item.setCartId(savedCart.getId()); // Подставляем сгенерированный базой Long id корзины
                    item.setProductId(id);
                    item.setQuantity(1);
                    return cartItemRepository.save(item);
                })
                .then();
    }

    private Mono<Void> setCartItem(Long id, String action, Long cartId) { // cartId теперь Long
        CartAction cartAction = CartAction.valueOf(action);

        return cartItemRepository.findByCartIdAndProductId(cartId, id)
                .switchIfEmpty(Mono.defer(() -> {
                    if (PLUS == cartAction) {
                        return cartItemRepository.save(new CartItem(null, cartId, id, 1, null))
                                .then(Mono.empty());
                    }
                    return Mono.empty();
                }))
                .flatMap(cartItem -> {
                    int quantity = cartItem.getQuantity();

                    if (quantity == 1 && MINUS == cartAction) {
                        return cartItemRepository.delete(cartItem);
                    }

                    CartItem updatedItem = null;
                    if (quantity > 1 && MINUS == cartAction) {
                        cartItem.setQuantity(quantity - 1);
                        updatedItem = cartItem;
                    } else if (quantity < Integer.MAX_VALUE && PLUS == cartAction) {
                        cartItem.setQuantity(quantity + 1);
                        updatedItem = cartItem;
                    }

                    return updatedItem != null ? cartItemRepository.save(updatedItem) : Mono.empty();
                })
                .then();
    }


//    @Transactional
//    public Mono<Void> changeItemsCount(Long id, String action, ServerHttpResponse response, String cartId) {
//
//        CartAction cartAction = CartAction.valueOf(action);
//
//        return productRepository.findById(id)
//                .flatMap(product -> {
//                    // Если cartId нет и действие PLUS — создаем новую корзину
//                    if (cartId == null || cartId.isEmpty()) {
//                        if (PLUS == cartAction) {
//                            String newCartId = UUID.randomUUID().toString();
//                            addCartCookie(response, newCartId);
//                            return createCartAndItem(id, newCartId);
//                        }
//                        return Mono.empty();
//                    }
//
//                    return cartRepository.findById(cartId)
//                            // Если кука есть, но корзины в БД нет (например, удалена)
//                            .switchIfEmpty(Mono.defer(() -> {
//                                if (PLUS == cartAction) {
//                                    return createCartAndItem(id, cartId).then(Mono.empty());
//                                }
//                                return Mono.empty();
//                            }))
//                            .flatMap(cart -> setCartItem(id, action, cartId));
//
//
//                });
//    }
//
//    private void addCartCookie(ServerHttpResponse response, String cartId) {
//        ResponseCookie cookie = ResponseCookie.from("cartId", cartId)
//                .maxAge(Duration.ofDays(7))
//                .path("/")
//                .httpOnly(true)
//                .build();
//        response.addCookie(cookie);
//    }
//
//    private Mono<Void> createCartAndItem(Long id, String cartId) {
//        Cart cart = new Cart();
//        cart.setId(cartId);
//
//        return cartRepository.save(cart)
//                .then(Mono.defer(() -> {
//                    CartItem item = new CartItem();
//                    item.setCartId(cartId);
//                    item.setProductId(id);
//                    item.setQuantity(1);
//                    // version и id не трогаем, они останутся null
//                    return cartItemRepository.save(item); }))
//                .then();
//    }
//
//    private Mono<Void> setCartItem(Long id, String action, String cartId) {
//
//        CartAction cartAction = CartAction.valueOf(action);
//        return cartItemRepository.findByCartIdAndProductId(cartId, id)
//                .switchIfEmpty(Mono.defer(() -> {
//                    if (PLUS == cartAction) {
//                        return cartItemRepository.save(new CartItem(null, cartId, id, 1, null))
//                                .then(Mono.empty());
//                    }
//                    return Mono.empty();
//                }))
//                .flatMap(cartItem -> {
//                    int quantity = cartItem.getQuantity();
//
//                    if (quantity == 1 && MINUS == cartAction) {
//                        return cartItemRepository.delete(cartItem);
//                    }
//
//                    CartItem updatedItem = null;
//                    if (quantity > 1 && MINUS == cartAction) {
//                        cartItem.setQuantity(quantity - 1);
//                        updatedItem = cartItem;
//                    } else if (quantity < Integer.MAX_VALUE && PLUS == cartAction) {
//                        cartItem.setQuantity(quantity + 1);
//                        updatedItem = cartItem;
//                    }
//
//                    return updatedItem != null ? cartItemRepository.save(updatedItem) : Mono.empty();
//                })
//                .then();
//    }

}
