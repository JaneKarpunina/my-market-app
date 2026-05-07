package ru.yandex.practicum.mymarket.service;

import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.dto.OrderFlatRow;
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

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
                                        return item;
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


    @Transactional
    public Mono<Long> saveOrder(String cartId, ServerHttpResponse response) {

        return orderRepository.save(new Order())
                .flatMap(savedOrder ->
                        cartItemRepository.findByCartId(cartId)
                                .map(ci -> {
                                    OrderItem oi = new OrderItem();
                                    oi.setOrderId(savedOrder.getId());
                                    oi.setProductId(ci.getProductId());
                                    oi.setQuantity(ci.getQuantity());
                                    return oi;
                                })
                                .collectList()
                                .flatMap(orderItems -> orderItemRepository.saveAll(orderItems)
                                        .collectList())
                                .then(cartRepository.deleteById(cartId))
                                .doOnSuccess(v -> deleteCookie(response))
                                .thenReturn(savedOrder.getId())
                );
    }

    private void deleteCookie(ServerHttpResponse response) {
        ResponseCookie cookie = ResponseCookie.from("cartId", "")
                .path("/")
                .maxAge(0)
                .build();
        response.addCookie(cookie);
    }

}
