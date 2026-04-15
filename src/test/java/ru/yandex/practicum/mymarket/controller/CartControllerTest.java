package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.CartService;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
public class CartControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CartService cartService;

    @Test
    void test_getCart_withExistingCart_shouldReturnCartViewWithItems() throws Exception {
        String cartId = "test-uuid";
        ItemDto item = new ItemDto();
        item.setTitle("Товар 1");
        item.setPrice(100L);
        item.setCount(2);

        CartDto mockCartDto = new CartDto(List.of(item), 200L);

        // Настройка поведения сервиса
        when(cartService.getCartDto(cartId)).thenReturn(mockCartDto);

        // Выполнение запроса с кукой
        mockMvc.perform(get("/cart/items")
                        .cookie(new Cookie("cartId", cartId)))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attribute("items", mockCartDto.getItems()))
                .andExpect(model().attribute("total", 200L));
    }

    @Test
    void test_getCart_withoutCookie_shouldReturnEmptyCart() throws Exception {
        CartDto emptyCartDto = new CartDto(List.of(), 0L);
        when(cartService.getCartDto(null)).thenReturn(emptyCartDto);

        mockMvc.perform(get("/cart/items"))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attribute("items", List.of()))
                .andExpect(model().attribute("total", 0L));
    }

    @Test
    void test_changeItemQuantity_shouldUpdateAndReturnCartView() throws Exception {
        Long itemId = 1L;
        String action = "PLUS";
        String cartId = "test-cart-id";

        CartDto updatedCart = new CartDto(List.of(new ItemDto()), 500L);

        doNothing().when(cartService).changeItemQuantity(itemId, action, cartId);
        when(cartService.getCartDto(cartId)).thenReturn(updatedCart);

        mockMvc.perform(post("/cart/items")
                        .param("id", itemId.toString())
                        .param("action", action)
                        .cookie(new Cookie("cartId", cartId)))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(model().attribute("items", updatedCart.getItems()))
                .andExpect(model().attribute("total", 500L));

        verify(cartService).changeItemQuantity(itemId, action, cartId);
        verify(cartService).getCartDto(cartId);
    }

    @Test
    void test_changeItemQuantity_withoutCookie_shouldStillWork() throws Exception {
        when(cartService.getCartDto(null)).thenReturn(new CartDto(List.of(), 0L));

        mockMvc.perform(post("/cart/items")
                        .param("id", "1")
                        .param("action", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("total", 0L));

        verify(cartService).changeItemQuantity(1L, "DELETE", null);
        verify(cartService).getCartDto(null);
    }

}
