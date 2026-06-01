package ru.yandex.practicum.mymarket.payment.integration;


import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.ApiClient;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.OrdersService;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
public class OrderProcessingIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private OrdersService orderService;

    @Autowired
    private ApiClient paymentApiClient;

    @MockBean
    private CartService cartService;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private CartItemRepository cartItemRepository;

    @MockBean
    private OrderItemRepository orderItemRepository;

    @MockBean
    private CartRepository cartRepository;

    private final String cartId = "cart-order-123";

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
        wireMockServer.resetAll();
        reset(cartService, orderRepository, cartItemRepository, orderItemRepository, cartRepository);

        paymentApiClient.setBasePath("http://localhost:" + wireMockServer.port());

        CartDto mockCartDto = new CartDto(
                List.of(new ItemDto(1L, "Товар",
                        "Описание", "img.png", 1000L, 1)),
                1000L
        );
        CartItem mockCartItem = new CartItem(10L, cartId, 1L, 1, 1L);
        Order mockSavedOrder = new Order();
        mockSavedOrder.setId(555L); // Фейковый ID созданного заказа

        doReturn(Mono.just(mockCartDto)).when(cartService).getCartDto(cartId);
        doReturn(Mono.just(mockSavedOrder)).when(orderRepository).save(any(Order.class));
        doReturn(Flux.just(mockCartItem)).when(cartItemRepository).findByCartId(cartId);
        doReturn(Flux.just(new OrderItem())).when(orderItemRepository).saveAll(any(Iterable.class));
        doReturn(Mono.empty()).when(cartRepository).deleteById(cartId);
    }

    @Test
    void processOrder_Success_ShouldPaySaveOrderAndDeleteCartWithCookie() {

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payment"))
                .withRequestBody(containing("\"amount\":1000"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{\"status\": \"success\"}")));

        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Long> result = orderService.processOrder(cartId, response);

        StepVerifier.create(result)
                .expectNext(555L)
                .verifyComplete();

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cartRepository, times(1)).deleteById(cartId);

        ResponseCookie cartCookie = response.getCookies().getFirst("cartId");
        assertNotNull(cartCookie);
        assertEquals("", cartCookie.getValue());
        assertEquals(0, cartCookie.getMaxAge().getSeconds());
    }

    @Test
    void processOrder_Failed_WhenClientError_ShouldThrowInsufficientFunds() {

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payment"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"error\": \"Not enough money\"}")));

        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Long> result = orderService.processOrder(cartId, response);

        StepVerifier.create(result)
                .expectErrorMessage("Оплата не прошла: недостаточно средств")
                .verify();

        verify(orderRepository, never()).save(any(Order.class));
        verify(cartRepository, never()).deleteById(anyString());

        assertNull(response.getCookies().getFirst("cartId"));
    }

    @Test
    void processOrder_Failed_WhenServerError_ShouldThrowServiceUnavailable() {

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payment"))
                .willReturn(aResponse()
                        .withStatus(500)));

        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Long> result = orderService.processOrder(cartId, response);

        StepVerifier.create(result)
                .expectErrorMessage("Сервис платежей временно недоступен")
                .verify();

        verify(orderRepository, never()).save(any(Order.class));
        verify(cartRepository, never()).deleteById(anyString());
    }

}
