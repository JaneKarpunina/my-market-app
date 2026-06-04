package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.CartItem;

import java.util.List;

@Repository
public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {

    Mono<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    Flux<CartItem> findByCartId(Long cartId);

    @Query("""
            SELECT * FROM cart_item
            WHERE cart_id = :cartId
              AND product_id IN (:ids)
            """)
    Flux<CartItem> findAllByCartIdAndProductIds(String cartId, List<Long> ids);
}
