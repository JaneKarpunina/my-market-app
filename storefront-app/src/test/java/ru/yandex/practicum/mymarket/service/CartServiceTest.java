package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = CartService.class)
public class CartServiceTest extends BaseTest {

    @Autowired
    private CartService cartService;

    private final Long productId = 1L;
    private final String cartId = "test-cart-123";
    private final String PLUS = "PLUS";
    private final String MINUS = "MINUS";
    private final String DELETE = "DELETE";

    @BeforeEach
    void resetMocks() {
        reset(cartRepository);
        reset(cartItemRepository);
    }

    @Test
    void getCartDto_EmptyCartId() {
        StepVerifier.create(cartService.getCartDto(""))
                .assertNext(dto -> {
                    assertTrue(dto.getItems().isEmpty());
                    assertEquals(0L, dto.getTotal());
                })
                .verifyComplete();

        verifyNoInteractions(cartRepository);
    }

    @Test
    void getCartDto_SuccessExistingCart() {

        ItemDto item1 = new ItemDto();
        item1.setId(1L);
        item1.setTitle("Product 1");
        item1.setPrice( 100L);
        item1.setCount(2);

        ItemDto item2 = new ItemDto();
        item2.setId(2L);
        item2.setTitle("Product 2");
        item2.setPrice( 50L);
        item2.setCount(1);

        Cart cart = new Cart();
        cart.setId(cartId);

        when(cartRepository.findById(cartId)).thenReturn(Mono.just(cart));
        when(cartRepository.findItemsForCartId(cartId)).thenReturn(Flux.just(item1, item2));

        StepVerifier.create(cartService.getCartDto(cartId))
                .assertNext(dto -> {
                    assertEquals(2, dto.getItems().size());
                    assertEquals(250L, dto.getTotal());
                })
                .verifyComplete();

        verify(cartRepository, never()).save(any());
    }

    @Test
    void getCartDto_CreateNewCartIfEmpty() {

        Cart cart = new Cart();
        cart.setId(cartId);

        when(cartRepository.findById(cartId)).thenReturn(Mono.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(Mono.just(cart));
        when(cartRepository.findItemsForCartId(cartId)).thenReturn(Flux.empty());

        StepVerifier.create(cartService.getCartDto(cartId))
                .assertNext(dto -> {
                    assertTrue(dto.getItems().isEmpty());
                    assertEquals(0L, dto.getTotal());
                })
                .verifyComplete();

        verify(cartRepository).save(argThat(c -> c.getId().equals(cartId)));
    }

    @Test
    void getCartDto_NoItemsInCart() {

        Cart cart = new Cart();
        cart.setId(cartId);

        when(cartRepository.findById(cartId)).thenReturn(Mono.just(cart));
        when(cartRepository.findItemsForCartId(cartId)).thenReturn(Flux.empty());

        StepVerifier.create(cartService.getCartDto(cartId))
                .assertNext(dto -> {
                    assertTrue(dto.getItems().isEmpty());
                    assertEquals(0L, dto.getTotal());
                })
                .verifyComplete();
    }

    @Test
    void changeQuantity_DeleteOnMinusOne() {
        CartItem item = new CartItem(10L, cartId, productId, 1, 0L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(item));
        when(cartItemRepository.delete(item)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeItemQuantity(productId, MINUS, cartId))
                .verifyComplete();

        verify(cartItemRepository).delete(item);
    }

    @Test
    void changeQuantity_DeleteOnAction() {
        CartItem item = new CartItem(10L, cartId, productId, 5, 0L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(item));
        when(cartItemRepository.delete(item)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeItemQuantity(productId, DELETE, cartId))
                .verifyComplete();

        verify(cartItemRepository).delete(item);
    }

    @Test
    void changeQuantity_Decrement() {
        CartItem item = new CartItem(10L, cartId, productId, 5, 0L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(item));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(Mono.just(item));

        StepVerifier.create(cartService.changeItemQuantity(productId, MINUS, cartId))
                .verifyComplete();

        verify(cartItemRepository).save(argThat(it -> it.getQuantity() == 4));
    }

    @Test
    void changeQuantity_Increment() {
        CartItem item = new CartItem(10L, cartId, productId, 2, 0L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(item));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(Mono.just(item));

        StepVerifier.create(cartService.changeItemQuantity(productId, PLUS, cartId))
                .verifyComplete();

        verify(cartItemRepository).save(argThat(it -> it.getQuantity() == 3));
    }

    @Test
    void changeQuantity_NotFound() {
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeItemQuantity(productId, PLUS, cartId))
                .verifyComplete();

        verify(cartItemRepository, never()).save(any());
        verify(cartItemRepository, never()).delete(any());
    }




}
