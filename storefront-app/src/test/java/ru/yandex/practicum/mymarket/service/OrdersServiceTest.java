package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderFlatRow;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = OrdersService.class)
public class OrdersServiceTest extends BaseTest {

    @Autowired
    private OrdersService ordersService;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderItemRepository orderItemRepository;

    @MockitoBean
    private ServerHttpResponse response;

    @BeforeEach
    void resetMocks() {
        reset(cartRepository);
        reset(cartItemRepository);
        reset(orderRepository);
        reset(orderItemRepository);
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
}
