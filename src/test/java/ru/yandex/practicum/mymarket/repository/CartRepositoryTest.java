package ru.yandex.practicum.mymarket.repository;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataR2dbcTest
public class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setup() {
        databaseClient.sql("DELETE FROM cart_item").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM product").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM cart").fetch().rowsUpdated().block();
    }

    @Test
    void findItemsForCartId_Success() {

        String cartId = "test-cart";

        insertTestData(cartId);

        cartRepository.findItemsForCartId(cartId)
                .as(StepVerifier::create)
                .assertNext(item -> {
                    assertEquals("Apple", item.getTitle());
                    assertEquals(5, item.getCount());
                })
                .assertNext(item -> {
                    assertEquals("Banana", item.getTitle());
                    assertEquals(10, item.getCount());
                })
                .verifyComplete();
    }

    @Test
    void findItemsForCartId_EmptyIfNoCart() {
        cartRepository.findItemsForCartId("non-existent")
                .as(StepVerifier::create)
                .verifyComplete(); // Должен быть пустой поток
    }

    private void insertTestData(String cartId) {
        databaseClient.sql("INSERT INTO cart (id) VALUES (:id)").bind("id", cartId).then().block();

        databaseClient.sql("INSERT INTO product (id, title, price, description, img_path) " +
                "VALUES (1, 'Apple', 100, 'desc', 'path1')").then().block();
        databaseClient.sql("INSERT INTO product (id, title, price, description, img_path)" +
                " VALUES (2, 'Banana', 50, 'desc', 'path2')").then().block();

        databaseClient.sql("INSERT INTO cart_item (cart_id, product_id, quantity) VALUES (:c, 1, 5)")
                .bind("c", cartId).then().block();
        databaseClient.sql("INSERT INTO cart_item (cart_id, product_id, quantity) VALUES (:c, 2, 10)")
                .bind("c", cartId).then().block();
    }
}
