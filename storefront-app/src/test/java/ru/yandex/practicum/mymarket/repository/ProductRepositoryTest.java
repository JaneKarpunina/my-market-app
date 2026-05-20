package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@DataR2dbcTest
public class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setup() {
        databaseClient.sql("DELETE FROM cart_item").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM product").fetch().rowsUpdated().block();
    }

    @Test
    void countByTitleAndDescription_Success() {
        insertProduct(1L, "Bread", "Food", 50L);
        insertProduct(2L, "Milk", "Food", 80L);
        insertProduct(3L, "Laptop", "Tech", 50000L);

        productRepository.countByTitleAndDescription("Food")
                .as(StepVerifier::create)
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void findProductWithQuantity_Success() {
        String cartId = "cart-2";
        insertProduct(10L, "Sony TV", "4K Display", 1500L);
        insertCartItem(cartId, 10L, 1);

        productRepository.findProductWithQuantity(10L, cartId)
                .as(StepVerifier::create)
                .assertNext(item -> {
                    assertEquals("Sony TV", item.getTitle());
                    assertEquals(1, item.getCount());
                })
                .verifyComplete();
    }

    @Test
    void findProductWithZeroCartId_Success() {
        insertProduct(5L, "Table", "Furniture", 200L);

        productRepository.findProductWithZeroCartId(5L)
                .as(StepVerifier::create)
                .assertNext(item -> {
                    assertEquals("Table", item.getTitle());
                    assertEquals(0, item.getCount());
                })
                .verifyComplete();
    }

    @Test
    void shouldFindIdsBySearchTermWithAlphabeticalOrder() {

        insertProduct(1L, "Apple iPhone 15", "Смартфон от Apple", 1000L);
        insertProduct(2L, "Samsung Galaxy S24", "Флагман от Samsung", 900L);
        insertProduct(3L, "Apple iPad Air", "Планшет Apple для работы", 600L);
        insertProduct(4L, "Xiaomi Redmi Note", "Бюджетный смартфон", 300L);

        Flux<Long> idsFlux = productRepository.findIdsOnly("Apple", "ALPHA", 10, 0);

        StepVerifier.create(idsFlux)
                .recordWith(ArrayList::new)
                .expectNextCount(2)
                .consumeRecordedWith(ids -> {
                    List<Long> expectedIds = List.of(3L, 1L);
                    assertIterableEquals(expectedIds, ids);
                })
                .verifyComplete();
    }

    private void insertProduct(Long id, String title, String desc, Long price) {
        databaseClient.sql("""
            INSERT INTO product (id, title, description, img_path, price)
            VALUES (:id, :title, :desc, 'img.jpg', :price)
            """)
                .bind("id", id)
                .bind("title", title)
                .bind("desc", desc)
                .bind("price", price)
                .then().block();
    }

    private void insertCartItem(String cartId, Long productId, int quantity) {
        databaseClient.sql("INSERT INTO cart (id) VALUES (:id)")
                .bind("id", cartId).then().block();

        databaseClient.sql("INSERT INTO cart_item (cart_id, product_id, quantity) VALUES (:c, :p, :q)")
                .bind("c", cartId)
                .bind("p", productId)
                .bind("q", quantity)
                .then().block();
    }

}
