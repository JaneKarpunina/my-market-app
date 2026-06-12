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
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
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
import ru.yandex.practicum.mymarket.api.PaymentApi;
import ru.yandex.practicum.mymarket.dto.CartDetailedResponse;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.redis.integration.EmbeddedRedisConfiguration;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;
import ru.yandex.practicum.mymarket.service.CartService;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmbeddedRedisConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CartPaymentIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private CartService cartService;

    @Autowired
    private PaymentApi paymentApi;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private CartRepository cartRepository;

    @MockBean
    private CartItemRepository cartItemRepository;

    @MockBean
    private ProductRepository productRepository;

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
    void setUpOAuth2Mock() {
        wireMockServer.resetAll();

        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("payment-service-client")
                .clientId("test-client")
                .tokenUri("http://localhost")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "fake-test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                clientRegistration,
                "anonymousUser",
                accessToken
        );
        Mockito.when(authorizedClientManager.authorize(Mockito.any()))
                .thenReturn(Mono.just(authorizedClient));
    }

    private void setupMockCartInDb(Long userId, Long cartId, Long productId, int quantity) {
        Cart mockCart = new Cart();
        mockCart.setId(cartId);
        mockCart.setUserId(userId);

        Mockito.when(cartRepository.findByUserId(userId))
                .thenReturn(Mono.just(mockCart));

        CartItem mockItem = new CartItem();
        mockItem.setCartId(cartId);
        mockItem.setProductId(productId);
        mockItem.setQuantity(quantity);

        Mockito.when(cartItemRepository.findByCartId(cartId))
                .thenReturn(Flux.just(mockItem));
    }

    @Test
    @WithMockUser(username = "customer_user")
    void getCartDetailed_Success() {
        Long userId = 1L;
        Long cartId = 100L;
        Long productId = 500L;
        long productPrice = 1500L;
        int quantity = 2;

        setupMockCartInDb(userId, cartId, productId, quantity);

        Product testProduct = new Product();
        testProduct.setId(productId);
        testProduct.setTitle("Тестовый товар");
        testProduct.setPrice(productPrice);
        redisTemplate.opsForValue().set("product:" + productId, testProduct).block();

        stubFor(get(urlPathMatching(".*balance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"amount\":5000}")));

        Mono<CartDetailedResponse> result = cartService.getCartDetailed(userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getBalance()).isEqualTo(5000L);
                    assertThat(response.getCart().getTotal()).isEqualTo(3000L);
                    assertThat(response.isCanOrder()).isTrue();
                    assertThat(response.getErrorMessage()).isNull();
                })
                .verifyComplete();

        verify(getRequestedFor(urlPathMatching(".*balance"))
                .withHeader("X-User-Id", equalTo("customer_user")));
    }

    @Test
    @WithMockUser(username = "customer_user")
    void getCartDetailed_InsufficientFunds() {
        Long userId = 1L;
        Long cartId = 100L;
        Long productId = 500L;
        long productPrice = 2000L;
        int quantity = 3;

        setupMockCartInDb(userId, cartId, productId, quantity);

        Product testProduct = new Product();
        testProduct.setId(productId);
        testProduct.setPrice(productPrice);
        redisTemplate.opsForValue().set("product:" + productId, testProduct).block();

        stubFor(get(urlPathMatching(".*balance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"amount\":1500}")));

        Mono<CartDetailedResponse> result = cartService.getCartDetailed(userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getBalance()).isEqualTo(1500L);
                    assertThat(response.getCart().getTotal()).isEqualTo(6000L);
                    assertThat(response.isCanOrder()).isFalse();
                    assertThat(response.getErrorMessage()).isEqualTo("Недостаточно средств");
                })
                .verifyComplete();
    }

    @Test
    @WithMockUser(username = "customer_user")
    void getCartDetailed_PaymentServiceUnavailable() {
        Long userId = 1L;
        Long cartId = 100L;

        Cart mockCart = new Cart();
        mockCart.setId(cartId);
        mockCart.setUserId(userId);

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.empty());

        stubFor(get(urlPathMatching(".*balance"))
                .willReturn(aResponse().withStatus(500)));

        Mono<CartDetailedResponse> result = cartService.getCartDetailed(userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getBalance()).isEqualTo(-1L);
                    assertThat(response.isCanOrder()).isFalse();
                    assertThat(response.getErrorMessage()).isEqualTo("Сервис платежей недоступен");
                })
                .verifyComplete();
    }
}

