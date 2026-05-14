package ru.yandex.practicum.mymarket.service;

import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.api.PaymentApi;
import ru.yandex.practicum.mymarket.domain.PaymentRequest;
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

    private final CartService cartService;

    private final PaymentApi paymentApi;

    public OrdersService(OrderRepository orderRepository, CartItemRepository cartItemRepository,
                         OrderItemRepository orderItemRepository, CartRepository cartRepository,
                         CartService cartService, PaymentApi paymentApi) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartService = cartService;
        this.paymentApi = paymentApi;
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

    public Mono<Long> processOrder(String cartId, ServerHttpResponse response) {

        return cartService.getCartDto(cartId)
                .flatMap(cartDto -> {

                    PaymentRequest paymentRequest = new PaymentRequest();
                    paymentRequest.setAmount(cartDto.getTotal());

                    return paymentApi.processPayment(paymentRequest)
                            .then(saveOrder(cartId, response));
                })
                .onErrorResume(WebClientResponseException.class, (WebClientResponseException ex) -> {
                    if (ex.getStatusCode().is4xxClientError()) {
                        return Mono.error(new RuntimeException("Оплата не прошла: недостаточно средств"));
                    }
                    return Mono.error(new RuntimeException("Сервис платежей временно недоступен"));
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
