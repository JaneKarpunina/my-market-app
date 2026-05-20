package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.api.PaymentApi;
import ru.yandex.practicum.mymarket.domain.PaymentRequest;
import ru.yandex.practicum.mymarket.domain.PaymentSuccessResponse;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderFlatRow;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = OrdersService.class)
public class OrdersServiceTest extends BaseTest {

    @Autowired
    private OrdersService ordersService;

    @MockBean
    private CartService cartService;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OrderItemRepository orderItemRepository;

    @MockBean
    private ServerHttpResponse response;

    @MockBean
    private PaymentApi paymentApi;

    private final String cartId = "cart-111";
    private final Long orderId = 777L;

    @BeforeEach
    void resetMocks() {
        reset(cartRepository);
        reset(cartItemRepository);
        reset(orderRepository);
        reset(orderItemRepository);
        reset(paymentApi);

        CartDto mockCartDto = new CartDto(List.of(), 1500L);
        lenient().when(cartService.getCartDto(cartId)).thenReturn(Mono.just(mockCartDto));
    }

    @Test
    void getOrders_Success() {

        OrderFlatRow row1 = new OrderFlatRow(1L, 100L, 1, 500L, "Product A");
        OrderFlatRow row2 = new OrderFlatRow(2L, 100L, 2, 250L, "Product B"); // Заказ 100, Товар B (итого 500+500=1000)
        OrderFlatRow row3 = new OrderFlatRow(3L, 200L, 1, 300L, "Product C"); // Заказ 200, Товар C (итого 300)

        when(orderRepository.findAllOrdersWithItems()).thenReturn(Flux.just(row1, row2, row3));

        StepVerifier.create(ordersService.getOrders())
                .assertNext(order -> {
                    assertEquals(100L, order.getId());
                    assertEquals(2, order.getItems().size());
                    assertEquals(1000L, order.getTotalSum());
                })
                .assertNext(order -> {
                    assertEquals(200L, order.getId());
                    assertEquals(1, order.getItems().size());
                    assertEquals(300L, order.getTotalSum());
                })
                .verifyComplete();

    }

    @Test
    void getOrders_Empty() {
        when(orderRepository.findAllOrdersWithItems()).thenReturn(Flux.empty());

        StepVerifier.create(ordersService.getOrders())
                .verifyComplete();
    }

    @Test
    void getOrder_Success() {
        Long orderId = 1L;
        OrderFlatRow row1 = new OrderFlatRow(10L, orderId, 2, 100L,  "Product A"); // 2 * 100 = 200
        OrderFlatRow row2 = new OrderFlatRow(20L, orderId, 1, 500L, "Product B"); // 1 * 500 = 500

        when(orderRepository.getOrder(orderId)).thenReturn(Flux.just(row1, row2));

        StepVerifier.create(ordersService.getOrder(orderId))
                .assertNext(orderDto -> {
                    assertEquals(orderId, orderDto.getId());
                    assertEquals(2, orderDto.getItems().size());
                    assertEquals(700L, orderDto.getTotalSum()); // 200 + 500

                    ItemDto firstItem = orderDto.getItems().getFirst();
                    assertEquals("Product A", firstItem.getTitle());
                    assertEquals(2, firstItem.getCount());
                })
                .verifyComplete();
    }

    @Test
    void getOrder_NotFound() {
        Long orderId = 999L;
        when(orderRepository.getOrder(orderId)).thenReturn(Flux.empty());

        StepVerifier.create(ordersService.getOrder(orderId))
                .assertNext(orderDto -> {
                    assertNull(orderDto.getId());
                    assertNull(orderDto.getItems());
                    assertEquals(0L, orderDto.getTotalSum());
                })
                .verifyComplete();
    }

    @Test
    void saveOrder_Success() {
        String cartId = "test-cart";
        Long savedOrderId = 10L;

        Order orderEntity = new Order();
        orderEntity.setId(savedOrderId);

        CartItem ci1 = new CartItem(1L, cartId, 101L, 2, 0L);
        CartItem ci2 = new CartItem(2L, cartId, 102L, 1, 0L);

        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(orderEntity));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(ci1, ci2));

        when(orderItemRepository.saveAll(anyIterable())).thenReturn(Flux.just(new OrderItem(), new OrderItem()));

        when(cartRepository.deleteById(cartId)).thenReturn(Mono.empty());

        StepVerifier.create(ordersService.saveOrder(cartId, response))
                .expectNext(savedOrderId)
                .verifyComplete();

        verify(orderItemRepository).saveAll((Iterable<OrderItem>) argThat(items -> {
            List<OrderItem> list = StreamSupport.stream(((Iterable<OrderItem>) items).spliterator(), false)
                    .collect(Collectors.toList());

            return list.size() == 2 && list.stream().allMatch(oi -> oi.getOrderId().equals(savedOrderId));
        }));

        verify(cartRepository).deleteById(cartId);
        verify(response).addCookie(argThat(cookie ->
                cookie.getName().equals("cartId") && cookie.getMaxAge().isZero()
        ));
    }

    @Test
    void processOrder_Success_ShouldCompletePaymentAndSaveOrder() {

        PaymentSuccessResponse paymentResponse = new PaymentSuccessResponse();
        paymentResponse.setStatus("success");
        when(paymentApi.processPayment(any(PaymentRequest.class))).thenReturn(Mono.just(paymentResponse));

        Order mockOrder = new Order();
        mockOrder.setId(orderId);
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(mockOrder));

        CartItem cartItem = new CartItem(1L, cartId, 42L, 2, 1L);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(cartItem));
        when(orderItemRepository.saveAll(any(Iterable.class))).thenReturn(Flux.just(new OrderItem()));
        when(cartRepository.deleteById(cartId)).thenReturn(Mono.empty());

        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Long> result = ordersService.processOrder(cartId, response);

        StepVerifier.create(result)
                .expectNext(orderId)
                .verifyComplete();

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cartRepository, times(1)).deleteById(cartId);

        ResponseCookie deletedCookie = response.getCookies().getFirst("cartId");
        assertNotNull(deletedCookie);
        assertEquals("", deletedCookie.getValue());
        assertEquals(0, deletedCookie.getMaxAge().getSeconds());
    }

    @Test
    void processOrder_Failed_WhenInsufficientFunds_ShouldThrowAndNotSaveOrder() {

        WebClientResponseException ex400 = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(), "Bad Request", null, null, null);

        when(paymentApi.processPayment(any(PaymentRequest.class))).thenReturn(Mono.error(ex400));

        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Long> result = ordersService.processOrder(cartId, response);

        StepVerifier.create(result)
                .expectErrorMessage("Оплата не прошла: недостаточно средств")
                .verify();

        verify(orderRepository, never()).save(any(Order.class));
        verify(cartRepository, never()).deleteById(anyString());

        assertNull(response.getCookies().getFirst("cartId"));
    }

    @Test
    void processOrder_Failed_WhenPaymentServiceIsDown_ShouldThrowServiceUnavailable() {

        WebClientResponseException ex500 = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Error",
                null, null, null);

        when(paymentApi.processPayment(any(PaymentRequest.class))).thenReturn(Mono.error(ex500));

        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Long> result = ordersService.processOrder(cartId, response);

        StepVerifier.create(result)
                .expectErrorMessage("Сервис платежей временно недоступен")
                .verify();

        verify(orderRepository, never()).save(any(Order.class));
        verify(cartRepository, never()).deleteById(anyString());
    }
}
