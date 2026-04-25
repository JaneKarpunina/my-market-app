package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Product;

@Repository
public interface ProductRepository extends ReactiveCrudRepository<Product, Long> {

    @Query("""
                SELECT p.*, COALESCE(ci.quantity, 0) as count
                FROM product p
                LEFT JOIN cart_item ci ON p.id = ci.product_id AND ci.cart_id = :cartId
                WHERE p.title ILIKE CONCAT('%', :search, '%') OR description ILIKE CONCAT('%', :search, '%')
                ORDER BY
                    CASE WHEN :sort = 'ALPHA' THEN p.title END ASC,
                    CASE WHEN :sort = 'PRICE' THEN p.price END ASC
                LIMIT :limit OFFSET :offset
            """)
    Flux<ItemDto> findProductsWithQuantityPaged(String search, String cartId, String sort, int limit, long offset);

    // С пагинацией без корзины
    @Query("""
            SELECT *, 0 as quantity FROM product
            WHERE title ILIKE CONCAT('%', :search, '%') OR description ILIKE CONCAT('%', :search, '%')
            ORDER BY
                CASE WHEN :sort = 'ALPHA' THEN title END ASC,
                CASE WHEN :sort = 'PRICE' THEN price END ASC
            LIMIT :limit OFFSET :offset
            """)
    Flux<ItemDto> findProductsWithZeroCartIdPaged(String search, String sort, int limit, long offset);

    @Query("""
             SELECT COUNT(*) FROM product
             WHERE title ILIKE CONCAT('%', :search, '%') OR description ILIKE CONCAT('%', :search, '%')
            """)
    Mono<Long> countByTitleAndDescription(String search);


    @Query("""
            SELECT p.*
            FROM product p
            WHERE p.id = :id
            """)
    Mono<ItemDto> findProductWithZeroCartId(Long id);

    @Query("""
            SELECT p.*,
            COALESCE(c.quantity, 0) as count
            FROM product p
            LEFT JOIN cart_item c ON c.product_id = p.id AND c.cart_id = :cartId
            WHERE p.id = :id
            """)
    Mono<ItemDto> findProductWithQuantity(Long id, String cartId);
}
