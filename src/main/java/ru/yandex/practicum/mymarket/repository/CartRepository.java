package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;

@Repository
public interface CartRepository extends ReactiveCrudRepository<Cart, String> {

    @Query("""
            SELECT p.*,
            c.quantity as count
            FROM product p
            JOIN cart_item c ON c.product_id = p.id AND c.cart_id = :cartId
            """)
    Flux<ItemDto> findItemsForCartId(String cartId);
}
