package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataR2dbcTest
public class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setup() {
        databaseClient.sql("DELETE FROM order_item").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM orders").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM product").fetch().rowsUpdated().block();
    }

    @Test
    void findAllOrdersWithItems_Success() {

        Long orderId = 100L;
        Long productId = 1L;
        insertTestData(orderId, productId, "Phone", 50000L, 2);

        orderRepository.findAllOrdersWithItems()
                .as(StepVerifier::create)
                .assertNext(row -> {
                    assertEquals(orderId, row.getOrderId());
                    assertEquals(productId, row.getProductId());
                    assertEquals("Phone", row.getTitle());
                    assertEquals(2, row.getQuantity());
                    assertEquals(50000L, row.getPrice());
                })
                .verifyComplete();
    }

    @Test
    void getOrder_ReturnsSpecificOrder() {

        insertTestData(100L, 1L, "Laptop", 70000L, 1);
        insertTestData(200L, 2L, "Mouse", 1000L, 3);

        orderRepository.getOrder(100L)
                .as(StepVerifier::create)
                .assertNext(row -> {
                    assertEquals(100L, row.getOrderId());
                    assertEquals("Laptop", row.getTitle());
                })
                .verifyComplete();
    }

    @Test
    void getOrder_ReturnsEmptyIfNotFound() {
        orderRepository.getOrder(999L)
                .as(StepVerifier::create)
                .verifyComplete();
    }

    private void insertTestData(Long orderId, Long productId, String title, Long price, int quantity) {

        databaseClient.sql("INSERT INTO orders (id) VALUES (:id)")
                .bind("id", orderId).then().block();

        databaseClient.sql("""
            INSERT INTO product (id, title, description, img_path, price)
            VALUES (:id, :title, 'Desc for ' || :title, 'img_' || :id || '.jpg', :price)
            """)
                .bind("id", productId)
                .bind("title", title)
                .bind("price", price)
                .then().block();

        databaseClient.sql("""
            INSERT INTO order_item (order_id, product_id, quantity)
            VALUES (:oid, :pid, :q)
            """)
                .bind("oid", orderId)
                .bind("pid", productId)
                .bind("q", quantity)
                .then().block();
    }
}
