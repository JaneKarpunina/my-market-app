package ru.yandex.practicum.mymarket.repository;

/*import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CartItemRepositoryTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void test_insertCartItem_shouldSaveDataToDatabase() {
        // 1. Подготовка данных (внешние ключи должны существовать)
        Product product = new Product(null, "Мышка", "Беспроводная", "mouse.jpg", 1200L);
        product = productRepository.save(product);

        String cartId = "cart-123";
        Cart cart = new Cart();
        cart.setId(cartId);
        cartRepository.save(cart);

        // 2. Выполнение нативного INSERT
        cartItemRepository.insertCartItem(cartId, product.getId(), 5);


        Optional<CartItem> savedItem = cartItemRepository.findByCartIdAndProductId(cartId, product.getId());

        assertTrue(savedItem.isPresent());
        assertEquals(5, savedItem.get().getQuantity());
        assertEquals(cartId, savedItem.get().getCart().getId());
        assertEquals("Мышка", savedItem.get().getProduct().getTitle());
    }

    @Test
    void test_findByCartIdAndProductId_shouldFetchRelations() {
        Product product = productRepository.save(new Product(null, "Наушники", "Bluetooth", "hp.jpg", 3000L));

        String cartId = "user-cart-777";
        Cart cart = new Cart();
        cart.setId(cartId);
        cart = cartRepository.save(cart);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(1);
        cartItemRepository.save(item);

        entityManager.clear();

        Optional<CartItem> result = cartItemRepository.findByCartIdAndProductId(cartId, product.getId());

        assertTrue(result.isPresent());
        CartItem found = result.get();

        assertEquals("Наушники", found.getProduct().getTitle());
        assertEquals(cartId, found.getCart().getId());
        assertEquals(1, found.getQuantity());
    }

    @Test
    void test_updateQuantity_shouldUpdateValueInDatabase() {
        Product product = productRepository.save(new Product(null, "Клавиатура",
                "Механика", "kb.jpg", 5000L));
        Cart cart = new Cart();
        cart.setId("update-cart-id");
        cart = cartRepository.save(cart);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(1);
        item = cartItemRepository.save(item);
        Long itemId = item.getId();

        int updatedRows = cartItemRepository.updateQuantity(itemId, 10);

        entityManager.clear();

        assertEquals(1, updatedRows);

        CartItem updatedItem = cartItemRepository.findById(itemId).orElseThrow();
        assertEquals(10, updatedItem.getQuantity());
    }

    @Test
    void test_findByCartId_ShouldReturnAllItemsWithFetchedRelations() {
        Product p1 = productRepository.save(new Product(null, "Товар 1", "Описание 1", "img1.jpg", 100L));
        Product p2 = productRepository.save(new Product(null, "Товар 2", "Описание 2", "img2.jpg", 200L));

        String targetCartId = "my-cart";
        Cart cart = new Cart();
        cart.setId(targetCartId);
        cart = cartRepository.save(cart);

        cartItemRepository.save(new CartItem(null, cart, p1, 1));
        cartItemRepository.save(new CartItem(null, cart, p2, 5));

        entityManager.clear();

        List<CartItem> results = cartItemRepository.findByCartId(targetCartId);

        assertEquals(2, results.size());

        for (CartItem item : results) {
            assertNotNull(item.getProduct().getTitle());
            assertEquals(targetCartId, item.getCart().getId());
        }

        // Проверяем конкретные значения
        assertTrue(results.stream().anyMatch(i -> i.getProduct().getTitle().equals("Товар 1") && i.getQuantity() == 1));
        assertTrue(results.stream().anyMatch(i -> i.getProduct().getTitle().equals("Товар 2") && i.getQuantity() == 5));
    }
}*/
