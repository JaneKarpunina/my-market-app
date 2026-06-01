package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartDetailedResponse;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.service.CartService;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@WebFluxTest(CartController.class)
public class CartControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    CartService cartService;

    @Test
    void shouldReturnCartViewWithModelAttributes() {
        CartDetailedResponse mockResponse = createMockCartData();
        String cartId = "test-cart-id";
        String error = "some-error";

        when(cartService.getCartDetailed(cartId)).thenReturn(Mono.just(mockResponse));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/cart/items")
                        .queryParam("error", error)
                        .build())
                .cookie("cartId", cartId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.contains("0"));
                });

    }

    @Test
    void shouldChangeItemQuantityAndReturnRenderingView() {
        CartDetailedResponse mockResponse = createMockCartData();
        String cartId = "test-cart-id";

        when(cartService.changeItemQuantity(eq(123L), eq("PLUS"), eq(cartId)))
                .thenReturn(Mono.empty());

        when(cartService.getCartDetailed(cartId))
                .thenReturn(Mono.just(mockResponse));

        webTestClient.post()
                .uri("/cart/items")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("id=123&action=PLUS")
                .cookie("cartId", cartId)
                .exchange()
                .expectStatus().isOk();

    }

    private CartDetailedResponse createMockCartData() {
        List<ItemDto> mockItems = Collections.emptyList();
        CartDto mockCart = new CartDto(mockItems, 0L);

        return new CartDetailedResponse(mockCart, 1000L, true, null);
    }
}
