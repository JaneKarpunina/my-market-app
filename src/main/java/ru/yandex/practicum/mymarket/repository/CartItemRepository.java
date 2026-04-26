package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.CartItem;

@Repository
public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {

    Mono<CartItem> findByCartIdAndProductId(String cartId, Long productId);

//    @Query("""
//              SELECT ci FROM CartItem ci
//              JOIN FETCH ci.product
//              JOIN FETCH ci.cart
//              WHERE ci.cart.id = :cartId
//              """)
//    List<CartItem> findByCartId(String cartId);
}
