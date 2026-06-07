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
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.*;

import java.util.List;
import java.util.Map;

@Service
public class OrdersService {

    private final OrderRepository orderRepository;

    private final CartItemRepository cartItemRepository;

    private final OrderItemRepository orderItemRepository;

    private final CartRepository cartRepository;

    private final ProductRepository productRepository;

    private final CartService cartService;

    private final PaymentApi paymentApi;

    public OrdersService(OrderRepository orderRepository, CartItemRepository cartItemRepository,
                         OrderItemRepository orderItemRepository, CartRepository cartRepository,
                         ProductRepository productRepository,
                         CartService cartService, PaymentApi paymentApi) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
        this.paymentApi = paymentApi;
    }


    @Transactional(readOnly = true)
    public Flux<OrderDto> getOrders(Long userId) {
        return orderRepository.findByUserId(userId)
                .map(Order::getId)
                .collectList()
                .flatMapMany(orderIds -> {
                    if (orderIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return orderItemRepository.findByOrderIdIn(orderIds)
                            .collectList()
                            .flatMapMany(this::processOrderItems);
                });
    }

    private Flux<OrderDto> processOrderItems(List<OrderItem> allItems) {
        if (allItems.isEmpty()) {
            return Flux.empty();
        }

        List<Long> productIds = extractUniqueProductIds(allItems);

        return productRepository.findByIdIn(productIds)
                .collectMap(Product::getId)
                .flatMapMany(productMap -> groupAndBuildOrderDtos(allItems, productMap));
    }

    private List<Long> extractUniqueProductIds(List<OrderItem> allItems) {
        return allItems.stream()
                .map(OrderItem::getProductId)
                .distinct()
                .toList();
    }

    private Flux<OrderDto> groupAndBuildOrderDtos(List<OrderItem> allItems, Map<Long, Product> productMap) {
        return Flux.fromIterable(allItems)
                .groupBy(OrderItem::getOrderId)
                .flatMap(groupedFlux -> {
                    Long orderId = groupedFlux.key();
                    return groupedFlux.collectList()
                            .map(orderItems -> buildOrderDto(orderId, orderItems, productMap));
                });
    }

    private OrderDto buildOrderDto(Long orderId, List<OrderItem> orderItems, Map<Long, Product> productMap) {
        List<ItemDto> itemDtos = orderItems.stream()
                .map(item -> mapToItemDto(item, productMap.get(item.getProductId())))
                .toList();

        long totalSum = itemDtos.stream()
                .mapToLong(item -> item.getPrice() * item.getCount())
                .sum();

        OrderDto orderDto = new OrderDto();
        orderDto.setId(orderId);
        orderDto.setItems(itemDtos);
        orderDto.setTotalSum(totalSum);
        return orderDto;
    }

    private ItemDto mapToItemDto(OrderItem item, Product product) {
        ItemDto itemDto = new ItemDto();
        itemDto.setId(item.getProductId());
        itemDto.setTitle(product.getTitle());
        itemDto.setPrice(product.getPrice());
        itemDto.setCount(item.getQuantity());
        return itemDto;
    }

    @Transactional(readOnly = true)
    public Mono<OrderDto> getOrder(Long id, Long userId) {
        return orderRepository.findById(id)
                .filter(order -> order.getUserId().equals(userId))
                .flatMap(order -> orderItemRepository.findByOrderId(id)
                        .collectList()
                        .flatMap(items -> processSingleOrder(id, items))
                )
                .defaultIfEmpty(new OrderDto());
    }

    private Mono<OrderDto> processSingleOrder(Long id, List<OrderItem> items) {
        if (items.isEmpty()) {
            return Mono.just(new OrderDto());
        }

        List<Long> productIds = extractUniqueProductIds(items);

        return productRepository.findByIdIn(productIds)
                .collectMap(Product::getId)
                .map(productMap -> buildOrderDto(id, items, productMap));
    }


    public Mono<Long> processOrder(Long userId) {

        return cartService.getCartDto(userId)
                .flatMap(cartDto -> {

                    PaymentRequest paymentRequest = new PaymentRequest();
                    paymentRequest.setAmount(cartDto.getTotal());

                    return paymentApi.processPayment(paymentRequest)
                            .flatMap(paymentResponse -> saveOrder(userId));
                })
                .onErrorResume(WebClientResponseException.class, (WebClientResponseException ex) -> {
                    if (ex.getStatusCode().is4xxClientError()) {
                        return Mono.error(new RuntimeException("Оплата не прошла: недостаточно средств"));
                    }
                    return Mono.error(new RuntimeException("Сервис платежей временно недоступен"));
                });
    }


    @Transactional
    public Mono<Long> saveOrder(Long userId) {

        return cartRepository.findByUserId(userId)
                .flatMap(cart -> {
                    Order newOrder = new Order();
                    newOrder.setUserId(userId);

                    return orderRepository.save(newOrder)
                            .flatMap(savedOrder ->
                                    cartItemRepository.findByCartId(cart.getId())
                                            .map(ci -> {
                                                OrderItem oi = new OrderItem();
                                                oi.setOrderId(savedOrder.getId());
                                                oi.setProductId(ci.getProductId());
                                                oi.setQuantity(ci.getQuantity());
                                                return oi;
                                            })
                                            .collectList()
                                            .flatMap(orderItems ->
                                                    orderItemRepository.saveAll(orderItems).collectList())
                                            .then(cartRepository.delete(cart))
                                            .thenReturn(savedOrder.getId())
                            );
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Корзина не найдена")));
    }

}
