package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrdersService {

    private final OrderRepository orderRepository;

    public OrdersService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<OrderDto> getOrders() {

        List<Order> orders = orderRepository.findAllWithItems();

        List<OrderDto> orderDtos = new ArrayList<>();

        for (Order order : orders) {
            OrderDto orderDto = getOrderDto(order);

            orderDtos.add(orderDto);
        }


        return orderDtos;
    }

    public OrderDto getOrder(Long id) {

        Order order = orderRepository.getOrder(id).orElse(null);

        if (order == null) {
            return new OrderDto();
        }

        return getOrderDto(order);
    }

    private OrderDto getOrderDto(Order order) {
        OrderDto orderDto = new OrderDto();

        orderDto.setId(order.getId());

        long totalSum = 0L;

        List<ItemDto> itemDtos = new ArrayList<>();
        for (OrderItem item : order.getItems()) {

            ItemDto itemDto = new ItemDto();
            Product product = item.getProduct();

            itemDto.setId(product.getId());
            itemDto.setTitle(product.getTitle());
            itemDto.setPrice(product.getPrice());
            itemDto.setCount(item.getQuantity());

            itemDtos.add(itemDto);

            totalSum += product.getPrice() * item.getQuantity();
        }

        orderDto.setItems(itemDtos);
        orderDto.setTotalSum(totalSum);
        return orderDto;
    }
}
