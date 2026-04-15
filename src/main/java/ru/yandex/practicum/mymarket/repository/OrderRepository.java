package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.mymarket.entity.Order;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.items oi
            JOIN FETCH oi.product
            """)
    List<Order> findAllWithItems();

    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.items oi
            JOIN FETCH oi.product
            WHERE o.id = :id
            """)
    Optional<Order> getOrder(Long id);
}
