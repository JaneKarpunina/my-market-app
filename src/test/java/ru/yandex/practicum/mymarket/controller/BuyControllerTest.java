package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.service.OrdersService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BuyController.class)
public class BuyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrdersService ordersService;

    @Test
    void test_buy_shouldCreateOrderAndRedirectToOrderDetails() throws Exception {
        String cartId = "test-cart-id";
        Long generatedOrderId = 123L;

        when(ordersService.saveOrder(eq(cartId), any(HttpServletResponse.class)))
                .thenReturn(generatedOrderId);

        mockMvc.perform(post("/buy")
                        .cookie(new Cookie("cartId", cartId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/123?newOrder=true"));
    }

    @Test
    void test_buy_withoutCookie() throws Exception {
        Long generatedOrderId = 456L;

        when(ordersService.saveOrder(eq(null), any(HttpServletResponse.class)))
                .thenReturn(generatedOrderId);

        mockMvc.perform(post("/buy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/456?newOrder=true"));
    }
}
