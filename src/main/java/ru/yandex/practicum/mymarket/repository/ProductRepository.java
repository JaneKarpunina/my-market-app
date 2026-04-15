package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Product;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT new ru.yandex.practicum.mymarket.dto.ItemDto(p.id, p.title, p.description, p.imgPath, p.price,
            COALESCE(c.quantity, 0))
            FROM Product p
            LEFT JOIN CartItem c ON c.product = p AND c.cart.id = :cartId
            WHERE (:search IS NULL OR p.title LIKE %:search% OR p.description LIKE %:search%)
            """)
    List<ItemDto> findProductsWithQuantity(String search, String cartId);

    @Query("""
            SELECT new ru.yandex.practicum.mymarket.dto.ItemDto(p.id, p.title, p.description, p.imgPath, p.price, 0)
            FROM Product p
            WHERE (:search IS NULL OR p.title LIKE %:search% OR p.description LIKE %:search%)
            """)
    List<ItemDto> findProductsWithZeroCartId(String search);

    @Query("""
            SELECT new ru.yandex.practicum.mymarket.dto.ItemDto(p.id, p.title, p.description, p.imgPath, p.price, 0)
            FROM Product p
            WHERE p.id = :id
            """)
    Optional<ItemDto> findProductWithZeroCartId(Long id);

    @Query("""
            SELECT new ru.yandex.practicum.mymarket.dto.ItemDto(p.id, p.title, p.description, p.imgPath, p.price,
            COALESCE(c.quantity, 0))
            FROM Product p
            LEFT JOIN CartItem c ON c.product = p AND c.cart.id = :cartId
            WHERE p.id = :id
            """)
    Optional<ItemDto> findProductWithQuantity(Long id, String cartId);
}
