package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.Cart;

@Repository
public interface CartRepository extends ReactiveCrudRepository<Cart, String> {

    Mono<Cart> findByUserId(Long userId);
}
