package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;

import java.util.List;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {

    @Query("""
            SELECT new ru.yandex.practicum.mymarket.dto.ItemDto(p.id, p.title, p.description, p.imgPath, p.price,
            c.quantity)
            FROM Product p
            JOIN CartItem c ON c.product = p AND c.cart.id = :cartId
            """)
    List<ItemDto> findItemsForCartId(String cartId);
}
