package ru.yandex.practicum.mymarket.controller;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.service.ItemsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@WebFluxTest(AllItemsController.class)
public class AllItemsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    ItemsService itemsService;

    @Test
    void getItems_Success() {

        Paging paging = new Paging(5, 1, false, false);
        ItemsWithPaging mockResponse = new ItemsWithPaging(List.of(), paging);

        when(itemsService.getItemsWithPaging(any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(Mono.just(mockResponse));

        webTestClient.get()
                .uri("/items")
                .cookie("cartId", "test-cart")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> {
                    String body = response.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.contains("NO"));
                });

        verify(itemsService).getItemsWithPaging(null, "NO", 1, 5, "test-cart");
    }




}
