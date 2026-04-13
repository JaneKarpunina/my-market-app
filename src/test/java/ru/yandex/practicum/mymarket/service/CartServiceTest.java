package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = CartService.class)
public class CartServiceTest extends BaseTest {

    @Autowired
    private CartService cartService;

    private final String CART_ID = "test-cart-uuid";
    private final Long ITEM_ID = 1L;
    private final String PLUS = "PLUS";
    private final String MINUS = "MINUS";
    private final String DELETE = "DELETE";

    @BeforeEach
    void resetMocks() {
        reset(cartRepository);
        reset(cartItemRepository);
    }

    @Test
    void test_getCartDto_withNullOrEmptyId_shouldReturnEmptyCartDto() {
        CartDto resultNull = cartService.getCartDto(null);
        CartDto resultEmpty = cartService.getCartDto("");

        assertTrue(resultNull.getItems().isEmpty());
        assertEquals(0L, resultNull.getTotal());
        assertTrue(resultEmpty.getItems().isEmpty());

        verifyNoInteractions(cartRepository);
    }

    @Test
    void test_getCartDto_whenCartNotFound_shouldCreateNewCartAndReturnEmptyDto() {

        when(cartRepository.findById(CART_ID)).thenReturn(Optional.empty());

        CartDto result = cartService.getCartDto(CART_ID);

        verify(cartRepository).save(argThat(cart -> cart.getId().equals(CART_ID)));
        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotal());
    }

    @Test
    void test_getCartDto_withItems_shouldCalculateTotalCorrectly() {

        when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(new Cart()));

        // Настройка: в корзине 2 товара
        ItemDto item1 = new ItemDto();
        item1.setPrice(100L);
        item1.setCount(2); // 100 * 2 = 200

        ItemDto item2 = new ItemDto();
        item2.setPrice(50L);
        item2.setCount(3);  // 50 * 3 = 150

        when(cartRepository.findItemsForCartId(CART_ID)).thenReturn(List.of(item1, item2));

        CartDto result = cartService.getCartDto(CART_ID);

        // Проверка суммы: 200 + 150 = 350
        assertEquals(350L, result.getTotal());
        assertEquals(2, result.getItems().size());
        verify(cartRepository).findItemsForCartId(CART_ID);
    }

    @Test
    void test_changeItemQuantity_shouldDoNothing_whenItemNotFound() {
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, ITEM_ID))
                .thenReturn(Optional.empty());

        cartService.changeItemQuantity(ITEM_ID, PLUS, CART_ID);

        verify(cartItemRepository, never()).delete(any());
        verify(cartItemRepository, never()).updateQuantity(anyLong(), anyInt());
    }

    @Test
    void test_changeItemQuantity_actionDelete_shouldAlwaysDelete() {

        CartItem item = new CartItem(10L, new Cart(), new Product(), 5); // Количество 5
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, ITEM_ID))
                .thenReturn(Optional.of(item));

        cartService.changeItemQuantity(ITEM_ID, DELETE, CART_ID);

        verify(cartItemRepository).delete(item);
    }

    @Test
    void test_changeItemQuantity_actionMinus_andQuantityIsOne() {

        CartItem item = new CartItem(10L, new Cart(), new Product(), 1); // Количество 1
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, ITEM_ID))
                .thenReturn(Optional.of(item));

        cartService.changeItemQuantity(ITEM_ID, MINUS, CART_ID);

        verify(cartItemRepository).delete(item);
        verify(cartItemRepository, never()).updateQuantity(anyLong(), anyInt());
    }

    @Test
    void test_changeItemQuantity_actionMinus_andQuantityGreaterThanOne_shouldDecrement() {
        CartItem item = new CartItem(10L, new Cart(), new Product(), 5);
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, ITEM_ID))
                .thenReturn(Optional.of(item));

        cartService.changeItemQuantity(ITEM_ID, MINUS, CART_ID);

        verify(cartItemRepository).updateQuantity(10L, 4);
    }

    @Test
    void test_changeItemQuantity_actionPlus_shouldIncrement() {
        CartItem item = new CartItem(10L, new Cart(), new Product(), 5);
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, ITEM_ID))
                .thenReturn(Optional.of(item));

        cartService.changeItemQuantity(ITEM_ID, PLUS, CART_ID);

        verify(cartItemRepository).updateQuantity(10L, 6);
    }

    @Test
    void test_changeItemQuantity_actionPlus_shouldNotExceedMaxInt() {
        CartItem item = new CartItem(10L, new Cart(), new Product(), Integer.MAX_VALUE);
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, ITEM_ID))
                .thenReturn(Optional.of(item));

        cartService.changeItemQuantity(ITEM_ID, PLUS, CART_ID);

        // Не должно быть вызова обновления, так как достигнут предел
        verify(cartItemRepository, never()).updateQuantity(anyLong(), anyInt());
    }



}
