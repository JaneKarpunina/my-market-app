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
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.entity.*;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

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
    private ProductRepository productRepository;

    @MockBean
    private PaymentApi paymentApi;

    private final Long userId = 1L;
    private final Long orderId = 777L;

    @BeforeEach
    void resetMocks() {
        reset(cartRepository);
        reset(cartItemRepository);
        reset(orderRepository);
        reset(orderItemRepository);
        reset(paymentApi);

        CartDto mockCartDto = new CartDto(List.of(), 1500L);
        lenient().when(cartService.getCartDto(userId)).thenReturn(Mono.just(mockCartDto));
    }

    @Test
    void getOrders_Success() {

        Long orderId = 100L;
        Long productId = 500L;

        Order mockOrder = new Order();
        mockOrder.setId(orderId);
        mockOrder.setUserId(userId);

        OrderItem mockItem = new OrderItem();
        mockItem.setOrderId(orderId);
        mockItem.setProductId(productId);
        mockItem.setQuantity(2);

        Product mockProduct = new Product();
        mockProduct.setId(productId);
        mockProduct.setTitle("Кофеварка");
        mockProduct.setPrice(1500L);

        when(orderRepository.findByUserId(userId)).thenReturn(Flux.just(mockOrder));
        when(orderItemRepository.findByOrderIdIn(List.of(orderId))).thenReturn(Flux.just(mockItem));
        when(productRepository.findByIdIn(List.of(productId))).thenReturn(Flux.just(mockProduct));

        StepVerifier.create(ordersService.getOrders(userId))
                .assertNext(orderDto -> {
                    assertEquals(orderId, orderDto.getId());
                    assertEquals(3000L, orderDto.getTotalSum());
                    assertEquals(1, orderDto.getItems().size());

                    ItemDto itemDto = orderDto.getItems().getFirst();
                    assertEquals(productId, itemDto.getId());
                    assertEquals("Кофеварка", itemDto.getTitle());
                    assertEquals(2, itemDto.getCount());
                })
                .verifyComplete();
    }


    @Test
    void getOrders_NoOrders_ReturnsEmptyFlux() {
        when(orderRepository.findByUserId(userId)).thenReturn(Flux.empty());

        StepVerifier.create(ordersService.getOrders(userId))
                .verifyComplete();
    }

    @Test
    void getOrder_Success() {

        Long orderId = 100L;
        Long productId = 500L;

        Order mockOrder = new Order();
        mockOrder.setId(orderId);
        mockOrder.setUserId(userId);

        OrderItem mockItem = new OrderItem();
        mockItem.setOrderId(orderId);
        mockItem.setProductId(productId);
        mockItem.setQuantity(3);

        Product mockProduct = new Product();
        mockProduct.setId(productId);
        mockProduct.setTitle("Клавиатура");
        mockProduct.setPrice(2000L);

        when(orderRepository.findById(orderId)).thenReturn(Mono.just(mockOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(Flux.just(mockItem));
        when(productRepository.findByIdIn(List.of(productId))).thenReturn(Flux.just(mockProduct));

        StepVerifier.create(ordersService.getOrder(orderId, userId))
                .assertNext(orderDto -> {
                    assertEquals(orderId, orderDto.getId());
                    assertEquals(6000L, orderDto.getTotalSum()); // 2000 * 3
                    assertEquals(1, orderDto.getItems().size());

                    ItemDto itemDto = orderDto.getItems().getFirst();
                    assertEquals("Клавиатура", itemDto.getTitle());
                    assertEquals(3, itemDto.getCount());
                })
                .verifyComplete();
    }

    @Test
    void getOrder_WrongUser_ReturnsEmptyOrderDto() {
        Long orderId = 100L;
        Long wrongUserId = 999L;

        Order mockOrder = new Order();
        mockOrder.setId(orderId);
        mockOrder.setUserId(wrongUserId);

        when(orderRepository.findById(orderId)).thenReturn(Mono.just(mockOrder));

        StepVerifier.create(ordersService.getOrder(orderId, userId))
                .assertNext(orderDto -> {
                    assertNull(orderDto.getId());
                    assertNull(orderDto.getItems());
                })
                .verifyComplete();
    }

    @Test
    void processOrder_Success() {

        Long cartId = 10L;
        Long orderId = 99L;
        Long productId = 500L;
        String idempotencyKey = "1";

        Cart mockCart = new Cart();
        mockCart.setId(cartId);
        mockCart.setUserId(userId);

        CartItem mockCartItem = new CartItem();
        mockCartItem.setCartId(cartId);
        mockCartItem.setProductId(productId);
        mockCartItem.setQuantity(2);

        Order mockOrder = new Order();
        mockOrder.setId(orderId);
        mockOrder.setUserId(userId);

        CartDto customCartDto = new CartDto(List.of(), 1500L);
        when(cartService.getCartDto(userId)).thenReturn(Mono.just(customCartDto));

        when(paymentApi.processPayment(any(PaymentRequest.class)))
                .thenReturn(Mono.just(new PaymentSuccessResponse()));

        when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(mockOrder));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(mockCartItem));
        when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.just(new OrderItem()));
        when(cartRepository.delete(mockCart)).thenReturn(Mono.empty());

        StepVerifier.create(ordersService.processOrder(userId, idempotencyKey))
                .expectNext(orderId)
                .verifyComplete();
    }

    @Test
    void processOrder_Payment4xxError_ReturnsCustomException() {
        String idempotencyKey = "1";

        WebClientResponseException mockException = WebClientResponseException.create(
                400, "Bad Request", null, null, null);

        when(paymentApi.processPayment(any(PaymentRequest.class))).thenReturn(Mono.error(mockException));

        StepVerifier.create(ordersService.processOrder(userId, idempotencyKey))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Оплата не прошла: недостаточно средств"))
                .verify();
    }

    @Test
    void processOrder_Payment5xxError_ReturnsCustomException() {
        String idempotencyKey = "1";

        WebClientResponseException mockException = WebClientResponseException.create(
                500, "Internal Server Error", null, null, null);

        when(paymentApi.processPayment(any(PaymentRequest.class))).thenReturn(Mono.error(mockException));

        StepVerifier.create(ordersService.processOrder(userId, idempotencyKey))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && throwable.getMessage().equals("Сервис платежей временно недоступен"))
                .verify();
    }
}
