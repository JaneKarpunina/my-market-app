package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.Product;

import java.util.Collection;

@Repository
public interface ProductRepository extends ReactiveCrudRepository<Product, Long> {

    @Query("""
            SELECT id FROM product
            WHERE title ILIKE CONCAT('%', :search, '%') OR description ILIKE CONCAT('%', :search, '%')
            ORDER BY
                CASE WHEN :sort = 'ALPHA' THEN title END ASC,
                CASE WHEN :sort = 'PRICE' THEN price END ASC
            LIMIT :limit OFFSET :offset
            """)
    Flux<Long> findIdsOnly(String search, String sort, int limit, long offset);

    @Query("""
             SELECT COUNT(*) FROM product
             WHERE title ILIKE CONCAT('%', :search, '%') OR description ILIKE CONCAT('%', :search, '%')
            """)
    Mono<Long> countByTitleAndDescription(String search);

    Flux<Product> findByIdIn(Collection<Long> ids);

}
