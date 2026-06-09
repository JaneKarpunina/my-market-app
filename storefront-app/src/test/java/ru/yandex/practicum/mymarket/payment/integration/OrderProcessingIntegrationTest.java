package ru.yandex.practicum.mymarket.payment.integration;


import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.ApiClient;
import ru.yandex.practicum.mymarket.api.PaymentApi;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.OrdersService;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class OrderProcessingIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private OrdersService orderService;

    @MockBean
    private CartService cartService;

    @Autowired
    private PaymentApi paymentApi;

    @MockBean
    private CartRepository cartRepository;

    @MockBean
    private CartItemRepository cartItemRepository;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OrderItemRepository orderItemRepository;

    @MockBean
    private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.payment-service.url", () -> wireMockServer.baseUrl());
    }

    @BeforeEach
    void setUpOAuth2() {
        wireMockServer.resetAll();

        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("payment-service-client")
                .clientId("test-client")
                .tokenUri("http://localhost")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "fake-token",
                Instant.now(), Instant.now().plusSeconds(3600));

        OAuth2AuthorizedClient authorizedClient =
                new OAuth2AuthorizedClient(clientRegistration, "anonymousUser", accessToken);

        Mockito.when(authorizedClientManager.authorize(Mockito.any())).thenReturn(Mono.just(authorizedClient));
    }

    @Test
    @WithMockUser(username = "customer_user")
    void processOrder_Success() {
        Long userId = 1L;
        Long cartId = 10L;
        Long expectedOrderId = 999L;

        CartDto mockCartDto = new CartDto(List.of(), 3500L);
        Mockito.when(cartService.getCartDto(userId)).thenReturn(Mono.just(mockCartDto));

        Cart dbCart = new Cart();
        dbCart.setId(cartId);
        dbCart.setUserId(userId);
        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(dbCart));

        Order savedOrder = new Order();
        savedOrder.setId(expectedOrderId);
        savedOrder.setUserId(userId);
        Mockito.when(orderRepository.save(Mockito.any(Order.class))).thenReturn(Mono.just(savedOrder));

        CartItem cartItem = new CartItem();
        cartItem.setCartId(cartId);
        cartItem.setProductId(500L);
        cartItem.setQuantity(2);
        Mockito.when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(cartItem));

        Mockito.when(orderItemRepository.saveAll(Mockito.anyCollection())).thenReturn(Flux.just(new OrderItem()));
        Mockito.when(cartRepository.delete(Mockito.any(Cart.class))).thenReturn(Mono.empty());

        stubFor(post(urlPathMatching(".*payment"))
                .withHeader("X-User-Id", equalTo("customer_user"))
                .withRequestBody(containing("\"amount\":3500"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\"}")));

        Mono<Long> result = orderService.processOrder(userId);

        StepVerifier.create(result)
                .expectNext(expectedOrderId)
                .verifyComplete();
    }

    @Test
    @WithMockUser(username = "customer_user")
    void processOrder_InsufficientFunds_ThrowsException() {
        Long userId = 1L;

        CartDto mockCartDto = new CartDto(List.of(), 100000L);
        Mockito.when(cartService.getCartDto(userId)).thenReturn(Mono.just(mockCartDto));

        stubFor(post(urlPathMatching(".*payment"))
                .withHeader("X-User-Id", equalTo("customer_user"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Недостаточно средств\"}")));

        Mono<Long> result = orderService.processOrder(userId);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Оплата не прошла: недостаточно средств"))
                .verify();
    }

    @Test
    @WithMockUser(username = "customer_user")
    void processOrder_ServiceUnavailable_ThrowsException() {
        Long userId = 1L;

        CartDto mockCartDto = new CartDto(List.of(), 500L);
        Mockito.when(cartService.getCartDto(userId)).thenReturn(Mono.just(mockCartDto));

        stubFor(post(urlPathMatching(".*payment"))
                .withHeader("X-User-Id", equalTo("customer_user"))
                .willReturn(aResponse()
                        .withStatus(500)));

        Mono<Long> result = orderService.processOrder(userId);
        
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Сервис платежей временно недоступен"))
                .verify();
    }
}
