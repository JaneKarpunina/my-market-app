package ru.yandex.practicum.mymarket.repository;

/*import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void test_findProductsWithQuantity_NoSearch_ReturnsAllProducts() {
        // Создаем продукты
        Product p1 = new Product();
        p1.setTitle("Apple");
        p1.setDescription("Fruit");
        p1.setImgPath("abc");
        p1.setPrice(10L);
        p1 = productRepository.save(p1);

        Product p2 = new Product();
        p2.setTitle("Banana");
        p2.setDescription("Fruit");
        p2.setImgPath("abc");
        p2.setPrice(5L);
        p2 = productRepository.save(p2);

        // Создаем корзину и CartItem для p1
        Cart cart = new Cart();
        cart.setId("cart123");
        cartRepository.save(cart);

        CartItem c1 = new CartItem();
        c1.setProduct(p1);
        c1.setCart(cart);
        c1.setQuantity(3);
        cartItemRepository.save(c1);

        // Вызов без поиска (search == null)
        List<ItemDto> result = productRepository.findProductsWithQuantity(null, "cart123");
        assertEquals(2, result.size());
        // Проверка для каждого продукта
        for (ItemDto item : result) {
            if (item.getId().equals(p1.getId())) {
                assertEquals(3, item.getCount());
            } else if (item.getId().equals(p2.getId())) {
                assertEquals(0, item.getCount());
            }
        }
    }

    @Test
    void test_findProductsWithQuantity_withSearch_filterResults() {
        // Создаём продукты
        Product p1 = new Product();
        p1.setTitle("Apple");
        p1.setDescription("Fruit");
        p1.setImgPath("abc");
        p1.setPrice(10L);
        p1 = productRepository.save(p1);

        Product p2 = new Product();
        p2.setTitle("Banana");
        p2.setDescription("Yellow Fruit");
        p2.setImgPath("abc");
        p2.setPrice(5L);
        p2 = productRepository.save(p2);

        // Создаём корзину и CartItem для p1
        Cart cart = new Cart();
        cart.setId("cart456");
        cartRepository.save(cart);

        CartItem c1 = new CartItem();
        c1.setProduct(p1);
        c1.setCart(cart);
        c1.setQuantity(2);
        cartItemRepository.save(c1);

        // Поиск по "Apple"
        List<ItemDto> results = productRepository.findProductsWithQuantity("Apple", "cart456");
        assertEquals(1, results.size());
        assertTrue(results.getFirst().getTitle().contains("Apple"));
        assertEquals(2, results.getFirst().getCount());

        // Поиск по слову, которого нет
        List<ItemDto> emptyResults = productRepository.findProductsWithQuantity("Nonexistent", "cart456");
        assertTrue(emptyResults.isEmpty());
    }

    @Test
    void test_findProductsWithZeroCartId_nullSearch_returnsMatchingProducts() {
        // Создаем продукты
        Product p1 = new Product();
        p1.setTitle("Apple");
        p1.setDescription("Fresh fruit");
        p1.setImgPath("abc");
        p1.setPrice(10L);
        productRepository.save(p1);

        Product p2 = new Product();
        p2.setTitle("Orange");
        p2.setDescription("Citrus fruit");
        p2.setImgPath("abc");
        p2.setPrice(8L);
        productRepository.save(p2);

        // Вызов метода без поиска
        List<ItemDto> results = productRepository.findProductsWithZeroCartId(null);

        // Проверка, что список не пустой
        assertFalse(results.isEmpty());
        // Проверка, что в результатах есть нужные продукты
        boolean foundP1 = false;
        boolean foundP2 = false;
        for (ItemDto item : results) {
            if (item.getId().equals(p1.getId())) {
                foundP1 = true;
                assertEquals(0, item.getCount());
            } else if (item.getId().equals(p2.getId())) {
                foundP2 = true;
                assertEquals(0, item.getCount());
            }
        }
        assertTrue(foundP1);
        assertTrue(foundP2);
    }

    @Test
    void test_findProductsWithZeroCartId_withSearchFilter() {
        // Создаем продукты
        Product p1 = new Product();
        p1.setTitle("Strawberry");
        p1.setDescription("Sweet");
        p1.setImgPath("abc");
        p1.setPrice(4L);
        productRepository.save(p1);

        Product p2 = new Product();
        p2.setTitle("Blueberry");
        p2.setDescription("Berry");
        p2.setImgPath("abc");
        p2.setPrice(5L);
        productRepository.save(p2);

        // Вызов с поиском по "Berry"
        List<ItemDto> results = productRepository.findProductsWithZeroCartId("Berry");
        // В результате должен быть только Blueberry
        assertEquals(1, results.size());
        ItemDto item = results.getFirst();
        assertEquals(p2.getId(), item.getId());
        assertEquals("Blueberry", item.getTitle());

        // Вызов с поиском по "Straw"
        results = productRepository.findProductsWithZeroCartId("Straw");
        assertEquals(1, results.size());
        item = results.getFirst();
        assertEquals(p1.getId(), item.getId());
        assertEquals("Strawberry", item.getTitle());

        // Вызов с поиском по несуществующему слову
        results = productRepository.findProductsWithZeroCartId("Nonexistent");
        assertTrue(results.isEmpty());
    }

    @Test
    void test_findProductWithZeroCartId_existingId() {
        // Создаем продукт
        Product p = new Product();
        p.setTitle("Grapes");
        p.setDescription("Sweet grapes");
        p.setImgPath("abc");
        p.setPrice(12L);
        p = productRepository.save(p);

        // Вызов метода по id
        Optional<ItemDto> resultOpt = productRepository.findProductWithZeroCartId(p.getId());

        // Проверяем, что результат есть
        assertTrue(resultOpt.isPresent());

        ItemDto item = resultOpt.get();
        assertEquals(p.getId(), item.getId());
        assertEquals("Grapes", item.getTitle());
        assertEquals(0, item.getCount());
    }

    @Test
    void test_findProductWithZeroCartId_nonExistingId() {
        // Используем несуществующий id
        Long nonExistingId = Long.MAX_VALUE;

        Optional<ItemDto> resultOpt = productRepository.findProductWithZeroCartId(nonExistingId);

        // Результат должен отсутствовать
        assertFalse(resultOpt.isPresent());
    }

    @Test
    void test_findProductWithQuantity_productInCart() {
        // Создаем продукт
        Product product = new Product();
        product.setTitle("Pineapple");
        product.setDescription("Tropical fruit");
        product.setImgPath("abc");
        product.setPrice(15L);
        product = productRepository.save(product);

        Cart cart = new Cart();
        cart.setId("cart456");
        cartRepository.save(cart);

        // Создаем CartItem с этим продуктом и корзиной
        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setCart(cart);
        cartItem.setQuantity(5);
        cartItemRepository.save(cartItem);

        Optional<ItemDto> resultOpt = productRepository.findProductWithQuantity(product.getId(), cart.getId());

        assertTrue(resultOpt.isPresent());
        ItemDto item = resultOpt.get();
        assertEquals(product.getId(), item.getId());
        assertEquals("Pineapple", item.getTitle());
        assertEquals(5, item.getCount());
    }

    @Test
    void test_findProductWithQuantity_productNotInCart_shouldReturnZero() {
        // Создаем продукт
        Product product = new Product();
        product.setTitle("Mango");
        product.setDescription("Tropical");
        product.setImgPath("abc");
        product.setPrice(20L);
        product = productRepository.save(product);

        // Не добавляем его в корзину

        // Вызов метода с несуществующей связью
        Optional<ItemDto> resultOpt = productRepository.findProductWithQuantity(product.getId(), "unknown_cart_id");

        assertTrue(resultOpt.isPresent(), "Должен быть результат");
        ItemDto item = resultOpt.get();
        assertEquals(product.getId(), item.getId());
        assertEquals("Mango", item.getTitle());
        assertEquals(0, item.getCount());
    }

    @Test
    void test_findProductWithQuantity_nonExistingProduct() {
        // Используем несуществующий id
        Optional<ItemDto> resultOpt = productRepository.findProductWithQuantity(99999L, "any_cart_id");
        assertFalse(resultOpt.isPresent());
    }


}*/
