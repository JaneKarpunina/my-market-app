package ru.yandex.practicum.mymarket.controller;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.entity.User;
import ru.yandex.practicum.mymarket.service.ItemsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
public class AllItemsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    ItemsService itemsService;

    @MockBean
    private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;
    @MockBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    private ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    @Test
    void getItems_ShouldPassUserIdToService_WhenUserIsAuthenticated() {
        Long userId = 99L;

        ItemsWithPaging mockPagingResponse = new ItemsWithPaging(List.of(), new Paging(5, 1, false, false));
        Mockito.when(itemsService.getItemsWithPaging("phone", "price", 1, 5, userId))
                .thenReturn(Mono.just(mockPagingResponse));

        User customUser = new User();
        customUser.setId(userId);
        customUser.setUsername("alex");

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                customUser, // Этот объект внедрится в @AuthenticationPrincipal
                null,
                List.of()
        );

        webTestClient
                .mutateWith(mockAuthentication(authToken))
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/items")
                        .queryParam("search", "phone")
                        .queryParam("sort", "price")
                        .queryParam("pageNumber", 1)
                        .queryParam("pageSize", 5)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(Assertions::assertNotNull);

        // Проверяем, что ID пользователя успешно дошел до сервиса
        Mockito.verify(itemsService, Mockito.times(1))
                .getItemsWithPaging("phone", "price", 1, 5, userId);
    }



}
