package ru.yandex.practicum.mymarket.controller;

/*import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.service.OrdersService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrdersController.class)
public class OrdersControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrdersService ordersService;

    @Test
    void test_getOrders_shouldReturnOrdersViewWithList() throws Exception {
        // Подготовка данных
        OrderDto order1 = new OrderDto();
        order1.setId(101L);
        order1.setTotalSum(500L);

        OrderDto order2 = new OrderDto();
        order2.setId(102L);
        order2.setTotalSum(1200L);

        List<OrderDto> mockOrders = List.of(order1, order2);

        // Настройка мока
        when(ordersService.getOrders()).thenReturn(mockOrders);

        // Выполнение запроса
        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(model().attribute("orders", mockOrders))
                .andExpect(model().attributeExists("orders"));
    }

    @Test
    void test_getOrder_shouldReturnOrderViewWithData() throws Exception {
        Long orderId = 1L;
        OrderDto mockOrder = new OrderDto();
        mockOrder.setId(orderId);
        mockOrder.setTotalSum(1500L);

        when(ordersService.getOrder(orderId)).thenReturn(mockOrder);

        mockMvc.perform(get("/orders/{id}", orderId)
                        .param("newOrder", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("order"))
                .andExpect(model().attribute("order", mockOrder))
                .andExpect(model().attribute("newOrder", true));
    }

    @Test
    void test_getOrder_withDefaultNewOrderParam_shouldBeFalse() throws Exception {
        Long orderId = 1L;
        when(ordersService.getOrder(orderId)).thenReturn(new OrderDto());

        // Вызов без параметра newOrder
        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(model().attribute("newOrder", false));
    }

    @Test
    void test_getOrder_whenOrderNotFound_shouldStillReturnView() throws Exception {
        Long orderId = 999L;
        when(ordersService.getOrder(orderId)).thenReturn(new OrderDto());

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("order"));
    }
}*/
