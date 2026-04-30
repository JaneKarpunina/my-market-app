package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.CartService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@WebFluxTest(CartController.class)
public class CartControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    CartService cartService;

    private final String cartId = "test-cart-123";

    @Test
    void getCart_ShouldReturnCartView() {
        // Given
        ItemDto item = new ItemDto();
        item.setTitle("Apple");
        item.setPrice(100L);
        item.setCount(2);

        CartDto cartDto = new CartDto(List.of(item), 200L);
        when(cartService.getCartDto(cartId)).thenReturn(Mono.just(cartDto));

        webTestClient.get()
                .uri("/cart/items")
                .cookie("cartId", cartId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.contains("Apple"));
                    assertTrue(body.contains("200"));
                });

        verify(cartService).getCartDto(cartId);
    }

    @Test
    void changeItemQuantity_ShouldUpdateAndReturnView() {

        Long productId = 1L;
        String action = "PLUS";
        CartDto updatedCart = new CartDto(List.of(), 500L);

        when(cartService.changeItemQuantity(eq(productId), eq(action), eq(cartId)))
                .thenReturn(Mono.empty());
        when(cartService.getCartDto(cartId))
                .thenReturn(Mono.just(updatedCart));

        webTestClient.post()
                .uri("/cart/items")
                .cookie("cartId", cartId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("id=" + productId + "&action=" + action)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    assertNotNull(body);
                });

        verify(cartService).changeItemQuantity(productId, action, cartId);
        verify(cartService).getCartDto(cartId);
    }
}
