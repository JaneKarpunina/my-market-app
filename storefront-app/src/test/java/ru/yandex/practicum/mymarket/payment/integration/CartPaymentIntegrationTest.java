package ru.yandex.practicum.mymarket.payment.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.ApiClient;
import ru.yandex.practicum.mymarket.dto.CartDetailedResponse;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.redis.integration.EmbeddedRedisConfiguration;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;
import ru.yandex.practicum.mymarket.service.CartService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest
@Import(EmbeddedRedisConfiguration.class)
public class CartPaymentIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private CartService cartService;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ApiClient paymentApiClient;

    @MockBean
    private CartRepository cartRepository;

    @MockBean
    private CartItemRepository cartItemRepository;

    @MockBean
    private ProductRepository productRepository;

    private final String cartId = "cart-payment-test";

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        redisTemplate.execute(connection -> connection.serverCommands().flushDb()).blockLast();
        wireMockServer.resetAll();
        reset(cartRepository, cartItemRepository, productRepository);

        paymentApiClient.setBasePath("http://localhost:" + wireMockServer.port());

        Cart mockCart = new Cart();
        mockCart.setId(cartId);
        CartItem item = new CartItem(1L, cartId, 99L, 2, 1L);
        Product product = new Product(99L, "Товар", "Описание",
                "img.png", 500L, 1L);
        doReturn(Mono.just(mockCart)).when(cartRepository).findById(cartId);
        doReturn(Flux.just(item)).when(cartItemRepository).findByCartId(cartId);
        doReturn(Flux.just(product)).when(productRepository).findAllById(any(Iterable.class));
    }

    @Test
    void getCartDetailed_Success_WhenBalanceIsEnough() {

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/balance"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        // Баланс 1500, стоимость корзины 1000 -> средств хватает
                        .withBody("{\"amount\": 1500}")));

        Mono<CartDetailedResponse> result = cartService.getCartDetailed(cartId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(1000L, response.getCart().getTotal());
                    assertEquals(1500L, response.getBalance());
                    assertTrue(response.isCanOrder());
                    assertNull(response.getErrorMessage());
                })
                .verifyComplete();
    }

    @Test
    void getCartDetailed_Failed_WhenBalanceIsNotEnough() {

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/balance"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{\"amount\": 800}")));

        Mono<CartDetailedResponse> result = cartService.getCartDetailed(cartId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(1000L, response.getCart().getTotal());
                    assertEquals(800L, response.getBalance());
                    assertFalse(response.isCanOrder());
                    assertEquals("Недостаточно средств", response.getErrorMessage());
                })
                .verifyComplete();
    }

    @Test
    void getCartDetailed_Failed_WhenPaymentServiceIsUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/balance"))
                .willReturn(aResponse()
                        .withStatus(500)));

        Mono<CartDetailedResponse> result = cartService.getCartDetailed(cartId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(-1L, response.getBalance());
                    assertFalse(response.isCanOrder());
                    assertEquals("Сервис платежей недоступен", response.getErrorMessage());
                })
                .verifyComplete();
    }


}
