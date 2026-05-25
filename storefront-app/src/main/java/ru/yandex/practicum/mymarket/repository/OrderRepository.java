package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.mymarket.entity.Order;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<Order, String> {
}
