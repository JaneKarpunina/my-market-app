package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.CartItem;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {

//    @Modifying
//    @Query(value = "INSERT INTO cart_item (cart_id, product_id, quantity) VALUES (:cartId, :productId, :quantity)",
//            nativeQuery = true)
//    void insertCartItem(String cartId, Long productId, int quantity);
//
//    @Query("""
//              SELECT ci FROM CartItem ci
//              JOIN FETCH ci.product
//              JOIN FETCH ci.cart
//              WHERE ci.cart.id = :cartId
//              AND ci.product.id = :productId
//              """)
    Mono<CartItem> findByCartIdAndProductId(String cartId, Long productId);
//
//    @Modifying
//    @Query(value = "UPDATE cart_item ci SET ci.quantity = :quantity WHERE ci.id = :id", nativeQuery = true)
//    int updateQuantity(Long id, int quantity);
//
//    @Query("""
//              SELECT ci FROM CartItem ci
//              JOIN FETCH ci.product
//              JOIN FETCH ci.cart
//              WHERE ci.cart.id = :cartId
//              """)
//    List<CartItem> findByCartId(String cartId);
}
