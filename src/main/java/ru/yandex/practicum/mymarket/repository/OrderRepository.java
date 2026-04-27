package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.yandex.practicum.mymarket.dto.OrderFlatRow;
import ru.yandex.practicum.mymarket.entity.Order;

import java.util.List;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<Order, String> {

    @Query("""
            SELECT o.id as order_id, oi.quantity, p.id as product_id, p.title, p.price
            FROM ORDERS o
            LEFT JOIN ORDER_ITEM oi ON o.id = oi.order_id
            LEFT JOIN PRODUCT p ON oi.product_id = p.id
            """)
    Flux<OrderFlatRow> findAllOrdersWithItems();

    @Query("""
            SELECT o.id as order_id, oi.quantity, p.id as product_id, p.title, p.price
            FROM ORDERS o
            LEFT JOIN ORDER_ITEM oi ON o.id = oi.order_id
            LEFT JOIN PRODUCT p ON oi.product_id = p.id
            WHERE o.id = :id
            """)
    Flux<OrderFlatRow> getOrder(Long id);
}
