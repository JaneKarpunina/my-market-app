package ru.yandex.practicum.mymarket.repository;

/*import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import ru.yandex.practicum.mymarket.entity.Order;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.entity.Product;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void test_findAllWithItems_ShouldLoadFullHierarchy() {
        // 1. Подготовка данных через репозитории
        Product product = new Product(null, "Клавиатура", "Механическая", "kb.jpg", 5000L);
        product = productRepository.save(product);

        Order order = new Order();
        order = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(2);
        orderItemRepository.save(item);

        entityManager.clear();

        // 2. Выполнение запроса
        List<Order> result = orderRepository.findAllWithItems();

        // 3. Проверки
        assertFalse(result.isEmpty());
        Order savedOrder = result.getFirst();

        assertEquals(1, savedOrder.getItems().size());

        OrderItem savedItem = savedOrder.getItems().getFirst();
        assertEquals("Клавиатура", savedItem.getProduct().getTitle());
        assertEquals(2, savedItem.getQuantity());
    }

    @Test
    void test_getOrder_shouldLoadOrderWithItemsAndProducts() {
        Product product = new Product(null, "Монитор", "4K", "mon.jpg", 30000L);
        product = productRepository.save(product);

        Order order = new Order();
        order = orderRepository.save(order);
        Long orderId = order.getId();

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        orderItemRepository.save(item);

        entityManager.clear();

        Optional<Order> result = orderRepository.getOrder(orderId);

        assertTrue(result.isPresent());
        Order foundOrder = result.get();

        assertEquals(orderId, foundOrder.getId());
        assertEquals(1, foundOrder.getItems().size());
        assertEquals("Монитор", foundOrder.getItems().getFirst().getProduct().getTitle());
    }

}*/
