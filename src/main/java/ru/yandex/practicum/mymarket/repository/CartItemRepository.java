package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.mymarket.entity.CartItem;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Modifying
    @Query(value = "INSERT INTO cart_item (cart_id, product_id, quantity) VALUES (:cartId, :productId, :quantity)",
            nativeQuery = true)
    void insertCartItem(String cartId, Long productId, int quantity);

    Optional<CartItem> findByCartIdAndProductId(String cartId, Long productId);

    @Modifying
    @Query(value = "UPDATE cart_item ci SET ci.quantity = :quantity WHERE ci.id = :id", nativeQuery = true)
    int updateQuantity(Long id, int quantity);
}
