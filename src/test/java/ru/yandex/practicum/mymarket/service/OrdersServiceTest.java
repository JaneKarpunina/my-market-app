package ru.yandex.practicum.mymarket.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    private final Long ORDER_ID = 1L;

    @BeforeEach
    void resetMocks() {
        reset(cartRepository);
        reset(cartItemRepository);
        reset(orderRepository);
        reset(orderItemRepository);
    }

    @Test
    void test_getOrders_shouldReturnEmptyList_whenNoOrdersExist() {
        when(orderRepository.findAllWithItems()).thenReturn(new ArrayList<>());

        List<OrderDto> result = ordersService.getOrders();

        assertTrue(result.isEmpty());
        verify(orderRepository).findAllWithItems();
    }

    @Test
    void test_getOrders_shouldCorrectlyMapOrdersAndCalculateTotalSum() {
        Product product = new Product();
        product.setId(10L);
        product.setTitle("Test Product");
        product.setPrice(200L);

        OrderItem orderItem = new OrderItem();
        orderItem.setProduct(product);
        orderItem.setQuantity(3); // 200 * 3 = 600

        Order order = new Order();
        order.setId(1L);
        orderItem.setOrder(order);
        order.setItems(List.of(orderItem));

        when(orderRepository.findAllWithItems()).thenReturn(List.of(order));

        // Вызов метода
        List<OrderDto> result = ordersService.getOrders();

        assertEquals(1, result.size());
        verify(orderRepository).findAllWithItems();
        OrderDto dto = result.getFirst();

        assertEquals(1L, dto.getId());
        assertEquals(600L, dto.getTotalSum()); // Проверка расчета суммы
        assertEquals(1, dto.getItems().size());

        ItemDto itemDto = dto.getItems().getFirst();
        assertEquals(10L, itemDto.getId());
        assertEquals("Test Product", itemDto.getTitle());
        assertEquals(200L, itemDto.getPrice());
        assertEquals(3, itemDto.getCount());
    }

    @Test
    void test_getOrder_whenOrderNotFound_shouldReturnEmptyOrderDto() {

        when(orderRepository.getOrder(ORDER_ID)).thenReturn(Optional.empty());

        OrderDto result = ordersService.getOrder(ORDER_ID);

        verify(orderRepository).getOrder(ORDER_ID);

        assertNotNull(result);
        assertNull(result.getId());
        assertNull(result.getItems());
    }

    @Test
    void test_getOrder_whenOrderExists_shouldMapCorrectlyAndCalculateTotal() {

        Product product = new Product();
        product.setId(50L);
        product.setTitle("Java Book");
        product.setPrice(1500L);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2); // 1500 * 2 = 3000

        Order order = new Order();
        order.setId(ORDER_ID);
        item.setOrder(order);
        order.setItems(List.of(item));

        when(orderRepository.getOrder(ORDER_ID)).thenReturn(Optional.of(order));

        OrderDto result = ordersService.getOrder(ORDER_ID);

        verify(orderRepository).getOrder(ORDER_ID);

        assertEquals(ORDER_ID, result.getId());
        assertEquals(3000L, result.getTotalSum());
        assertEquals(1, result.getItems().size());

        ItemDto itemDto = result.getItems().getFirst();
        assertEquals("Java Book", itemDto.getTitle());
        assertEquals(2, itemDto.getCount());
        assertEquals(1500L, itemDto.getPrice());
    }

    @Test
    void test_saveOrder_shouldCreateOrderAndClearCart() {
        String cartId = "test-cart-id";
        MockHttpServletResponse response = new MockHttpServletResponse();

        Product product = new Product();
        product.setId(1L);

        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2);

        Order savedOrder = new Order();
        savedOrder.setId(100L);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        Long orderId = ordersService.saveOrder(cartId, response);

        assertEquals(100L, orderId);

        // 1. Проверяем сохранение элементов заказа
        verify(orderItemRepository).saveAll(argThat(items -> {
            List<OrderItem> list = (List<OrderItem>) items;
            return list.size() == 1 &&
                    list.getFirst().getQuantity() == 2 &&
                    list.getFirst().getOrder().getId().equals(100L);
        }));

        // 2. Проверяем удаление корзины
        verify(cartRepository).deleteById(cartId);

        // 3. Проверяем удаление Cookie
        Cookie cookie = response.getCookie("cartId");
        assertNotNull(cookie);
        assertEquals(0, cookie.getMaxAge());
        assertNull(cookie.getValue());
    }
}
