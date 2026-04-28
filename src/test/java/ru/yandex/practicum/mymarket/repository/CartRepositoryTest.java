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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void test_findItemsForCartId_shouldReturnCorrectDtos() {
        Product product = new Product(null, "Кофе", "Арабика", "coffee.jpg", 500L);
        product = productRepository.save(product);

        String cartId = "cart-unique-id";
        Cart cart = new Cart();
        cart.setId(cartId);
        cartRepository.save(cart);

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(3);
        cartItemRepository.save(cartItem);

        List<ItemDto> result = cartRepository.findItemsForCartId(cartId);

        assertEquals(1, result.size());
        ItemDto dto = result.getFirst();

        assertEquals("Кофе", dto.getTitle());
        assertEquals(500L, dto.getPrice());
        assertEquals(3, dto.getCount());
        assertEquals(product.getId(), dto.getId());
    }

    @Test
    void test_findItemsForCartId_shouldNotReturnItemsFromOtherCarts() {
        Product p = productRepository.save(new Product(null, "Чай", "Зеленый", "tea.jpg", 200L));
        Cart cart1 = new Cart();
        cart1.setId("cart-1");
        Cart cart2 = new Cart();
        cart2.setId("cart-2");
        cart1 = cartRepository.save(cart1);
        cart2 = cartRepository.save(cart2);

        // Кладем товар только в первую корзину
        cartItemRepository.save(new CartItem(null, cart1, p, 10));

        // Запрашиваем данные для второй корзины
        List<ItemDto> result = cartRepository.findItemsForCartId("cart-2");

        // Должно быть пусто
        assertTrue(result.isEmpty());
    }
}*/
