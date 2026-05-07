package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.ItemsService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@WebFluxTest(ItemsController.class)
public class ItemsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    ItemsService itemsService;

    private final String cartId = "test-cart";

    @Test
    void changeItemQuantity_RedirectWithParams() {
        when(itemsService.changeItemsCount(anyLong(), anyString(), any(ServerHttpResponse.class), anyString()))
                .thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/items")
                .cookie("cartId", cartId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("id", "1")
                        .with("action", "PLUS")
                        .with("search", "phone")
                        .with("sort", "PRICE")
                        .with("pageNumber", "2")
                        .with("pageSize", "10"))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueEquals("Location", "/items?search=phone&sort=PRICE&pageNumber=2&pageSize=10");

        verify(itemsService).changeItemsCount(eq(1L), eq("PLUS"), any(), eq(cartId));
    }

    @Test
    void getItem_ShouldReturnItemView() {
        ItemDto mockItem = new ItemDto();
        mockItem.setId(1L);
        mockItem.setTitle("Test Product");

        when(itemsService.getItemWithQuantity(1L, cartId)).thenReturn(Mono.just(mockItem));

        webTestClient.get()
                .uri("/items/1")
                .cookie("cartId", cartId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.contains("Test Product"));
                });

        verify(itemsService).getItemWithQuantity(1L, cartId);
    }

    @Test
    void changeItemQuantity_SingleItem_ReturnRendering() {
        ItemDto updatedItem = new ItemDto();
        updatedItem.setId(1L);
        updatedItem.setCount(5);

        when(itemsService.changeItemsCount(anyLong(), anyString(), any(ServerHttpResponse.class), anyString()))
                .thenReturn(Mono.empty());
        when(itemsService.getItemWithQuantity(1L, cartId)).thenReturn(Mono.just(updatedItem));

        webTestClient.post()
                .uri("/items/1")
                .cookie("cartId", cartId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("action", "PLUS"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.contains("5"));
                });
        verify(itemsService).changeItemsCount(eq(1L), eq("PLUS"), any(), eq(cartId));
        verify(itemsService).getItemWithQuantity(1L, cartId);
    }




}
