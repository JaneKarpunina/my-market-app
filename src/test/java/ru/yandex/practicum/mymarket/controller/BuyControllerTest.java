package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.OrdersService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(BuyController.class)
class BuyControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private OrdersService ordersService;

    @Test
    void buy_ShouldSaveOrderAndRedirect() {

        String cartId = "test-cart-id";
        Long generatedOrderId = 123L;

        when(ordersService.saveOrder(eq(cartId), any(ServerHttpResponse.class)))
                .thenReturn(Mono.just(generatedOrderId));

        webTestClient.post()
                .uri("/buy")
                .cookie("cartId", cartId)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueEquals("Location", "/orders/123?newOrder=true");

        verify(ordersService).saveOrder(eq(cartId), any(ServerHttpResponse.class));
    }

    @Test
    void buy_WithoutCookie() {
        Long generatedOrderId = 456L;

        when(ordersService.saveOrder(isNull(), any(ServerHttpResponse.class)))
                .thenReturn(Mono.just(generatedOrderId));

        webTestClient.post()
                .uri("/buy")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueEquals("Location", "/orders/456?newOrder=true");
    }
}
