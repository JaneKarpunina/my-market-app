package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.OrdersService;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WebFluxTest(BuyController.class)
class BuyControllerTest {

    @Autowired
    private WebTestClient webTestClient;

//    @Autowired
//    private ApplicationContext applicationContext;

    @MockBean
    private OrdersService ordersService;

    private final String cartId = "cart-webflux-test-123";

//    @BeforeEach
//    void setUp() {
//        this.webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
//                .configureClient()
//                .build();
//    }

    @Test
    void buy_Success_ShouldRedirectToOrderDetails() {
        Long generatedOrderId = 999L;

        when(ordersService.processOrder(eq(cartId), any(ServerHttpResponse.class)))
                .thenReturn(Mono.just(generatedOrderId));

        webTestClient.post()
                .uri("/buy")
                .cookie("cartId", cartId)
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/orders/999?newOrder=true");

        verify(ordersService, times(1)).processOrder(eq(cartId), any(ServerHttpResponse.class));
    }

    @Test
    void buy_Failed_ShouldRedirectToCartWithEncodedErrorMessage() {
        String errorMessage = "Оплата не прошла: недостаточно средств";

        when(ordersService.processOrder(eq(cartId), any(ServerHttpResponse.class)))
                .thenReturn(Mono.error(new RuntimeException(errorMessage)));

        String encodedError = UriUtils.encode(errorMessage, StandardCharsets.UTF_8);

        webTestClient.post()
                .uri("/buy")
                .cookie("cartId", cartId)
                .exchange()
                .expectStatus().isSeeOther()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/cart/items?error=" + encodedError);
    }
}
