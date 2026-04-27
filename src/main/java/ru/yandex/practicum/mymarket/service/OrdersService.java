package ru.yandex.practicum.mymarket.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderFlatRow;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrdersService {

    private final OrderRepository orderRepository;

    private final CartItemRepository cartItemRepository;

    private final OrderItemRepository orderItemRepository;

    private final CartRepository cartRepository;

    public OrdersService(OrderRepository orderRepository, CartItemRepository cartItemRepository,
                         OrderItemRepository orderItemRepository, CartRepository cartRepository) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
    }

    @Transactional(readOnly = true)
    public Flux<OrderDto> getOrders() {

        return orderRepository.findAllOrdersWithItems()
                .groupBy(OrderFlatRow::getOrderId)
                .flatMap(groupedFlux -> groupedFlux
                        .collectList()
                        .map(rows -> {
                            Long orderId = groupedFlux.key();

                            List<ItemDto> items = rows.stream()
                                    .map(row -> {
                                        ItemDto item = new ItemDto();
                                        item.setId(row.getProductId());
                                        item.setTitle(row.getTitle());
                                        item.setCount(row.getQuantity());
                                        item.setPrice(row.getPrice());
                                        return new ItemDto();
                                    })
                                    .toList();

                            // Считаем сумму
                            long total = items.stream()
                                    .mapToLong(i -> i.getPrice() * i.getCount())
                                    .sum();

                            return new OrderDto(orderId, items, total);
                        })
                );
    }

    @Transactional(readOnly = true)
    public Mono<OrderDto> getOrder(Long id) {

        return orderRepository.getOrder(id)
                .collectList()
                .flatMap(rows -> {
                            if (rows.isEmpty()) {
                                return Mono.just(new OrderDto());
                            }

                            OrderDto orderDto = new OrderDto();
                            orderDto.setId(rows.getFirst().getOrderId());

                            List<ItemDto> itemDtos = rows.stream()
                                    .map(row -> {
                                        ItemDto itemDto = new ItemDto();
                                        itemDto.setId(row.getProductId());
                                        itemDto.setTitle(row.getTitle());
                                        itemDto.setPrice(row.getPrice());
                                        itemDto.setCount(row.getQuantity());
                                        return itemDto;
                                    })
                                    .toList();
                    long totalSum = itemDtos.stream()
                            .mapToLong(item -> item.getPrice() * item.getCount())
                            .sum();

                    orderDto.setItems(itemDtos);
                    orderDto.setTotalSum(totalSum);

                    return Mono.just(orderDto);
                });

    }

//    private OrderDto getOrderDto(Order order) {
//        OrderDto orderDto = new OrderDto();
//
//        orderDto.setId(order.getId());
//
//        long totalSum = 0L;
//
//        List<ItemDto> itemDtos = new ArrayList<>();
//        for (OrderItem item : order.getItems()) {
//
//            ItemDto itemDto = new ItemDto();
//            Product product = item.getProduct();
//
//            itemDto.setId(product.getId());
//            itemDto.setTitle(product.getTitle());
//            itemDto.setPrice(product.getPrice());
//            itemDto.setCount(item.getQuantity());
//
//            itemDtos.add(itemDto);
//
//            totalSum += product.getPrice() * item.getQuantity();
//        }
//
//        orderDto.setItems(itemDtos);
//        orderDto.setTotalSum(totalSum);
//        return orderDto;
//    }

    @Transactional
    public Long saveOrder(String cartId, HttpServletResponse response) {

//        List<CartItem> cartItems = cartItemRepository.findByCartId(cartId);
//
//        Order order = new Order();
//        order = orderRepository.save(order);
//
//        List<OrderItem> orderItems = new ArrayList<>();
//
//        for (CartItem ci : cartItems) {
//            OrderItem orderItem = new OrderItem();
//            orderItem.setProduct(ci.getProduct());
//            orderItem.setQuantity(ci.getQuantity());
//            orderItem.setOrder(order);
//
//            orderItems.add(orderItem);
//        }
//
//        orderItemRepository.saveAll(orderItems);
//
//        cartRepository.deleteById(cartId);
//
//        deleteCookie(response);
//
//        return order.getId();

        return 0L;
    }

    private void deleteCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("cartId", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

}
