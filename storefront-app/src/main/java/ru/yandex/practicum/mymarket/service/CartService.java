package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.api.PaymentApi;
import ru.yandex.practicum.mymarket.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.dto.CartDetailedResponse;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;

import java.util.List;

@Service
public class CartService {

    public static final String PLUS = "PLUS";
    public static final String MINUS = "MINUS";
    public static final String DELETE = "DELETE";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PaymentApi paymentApi;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       PaymentApi paymentApi) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.paymentApi = paymentApi;
    }

    @Transactional
    public Mono<CartDto> getCartDto(String cartId) {

        if (cartId == null || cartId.isEmpty()) {
            return Mono.just(new CartDto(List.of(), 0L));
        }

        return cartRepository.findById(cartId)
                .switchIfEmpty(Mono.defer(() -> {
                    Cart newCart = new Cart();
                    newCart.setId(cartId);
                    return cartRepository.save(newCart);
                }))
                .flatMap(cart -> cartRepository.findItemsForCartId(cartId)
                        .collectList()
                        .map(items -> {
                            long total = items.stream()
                                    .mapToLong(item -> item.getPrice() * item.getCount())
                                    .sum();
                            return new CartDto(items, total);
                        })
                );

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
