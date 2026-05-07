package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.service.OrdersService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@WebFluxTest(OrdersController.class)
public class OrdersControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    OrdersService ordersService;

    @Test
    void getOrders_ShouldReturnOrdersView() {

        OrderDto order1 = new OrderDto(100L, List.of(), 1000L);
        OrderDto order2 = new OrderDto(200L, List.of(), 2000L);

        when(ordersService.getOrders()).thenReturn(Flux.just(order1, order2));

        webTestClient.get()
                .uri("/orders")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.contains("1000"));
                });
    }

    @Test
    void getOrder_ShouldReturnOrderViewWithFlag() {

        Long orderId = 1L;
        OrderDto mockOrder = new OrderDto(orderId, List.of(), 500L);

        when(ordersService.getOrder(orderId)).thenReturn(Mono.just(mockOrder));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders/{id}")
                        .queryParam("newOrder", "true")
                        .build(orderId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.contains("500"));
                });
    }


}
