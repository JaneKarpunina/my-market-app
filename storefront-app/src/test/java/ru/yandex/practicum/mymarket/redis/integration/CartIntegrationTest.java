package ru.yandex.practicum.mymarket.redis.integration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;
import ru.yandex.practicum.mymarket.service.CartService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(EmbeddedRedisConfiguration.class)
public class CartIntegrationTest {

    @Autowired
    CartService cartService;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private CartItemRepository cartItemRepository;


    @MockBean
    private CartRepository cartRepository;


//    @Test
//    void shouldFetchCartAndCacheMissingProductsOnFirstCall_thenUseRedisOnSecondCall() {
//        String cartId = "test-cart-777";
//        Long prodId1 = 101L;
//        Long prodId2 = 102L;
//
//        // Данные для эмуляции БД
//        Cart mockCart = new Cart();
//        mockCart.setId(cartId);
//
//        CartItem item1 = new CartItem(1L, cartId, prodId1, 2, 1L); // 2 шт. товара 101 (цена 100)
//        CartItem item2 = new CartItem(2L, cartId, prodId2, 5, 1L); // 5 шт. товара 102 (цена 200)
//
//        Product product1 = new Product(prodId1, "Товар 101",
//                "Описание 101", "img101.png", 100L, 1L);
//        Product product2 = new Product(prodId2, "Товар 102",
//                "Описание 102", "img102.png", 200L, 1L);
//
//        doReturn(Mono.just(mockCart)).when(cartRepository).findById(cartId);
//        doReturn(Flux.just(item1, item2)).when(cartItemRepository).findByCartId(cartId);
//        doReturn(Flux.just(product1, product2)).when(productRepository).findAllById(any(Iterable.class));
//
//        Mono<CartDto> firstCall = cartService.getCartDto(cartId);
//
//        StepVerifier.create(firstCall)
//                .assertNext(cartDto -> {
//                    assertNotNull(cartDto);
//                    assertEquals(2, cartDto.getItems().size());
//
//                    // Проверяем подсчет общей стоимости: (100 * 2) + (200 * 5) = 200 + 1000 = 1200
//                    assertEquals(1200L, cartDto.getTotal());
//
//                    // Проверяем маппинг полей одного из товаров
//                    ItemDto dto101 = cartDto.getItems().stream()
//                            .filter(i -> i.getId().equals(prodId1))
//                            .findFirst()
//                            .orElseThrow();
//                    assertEquals("Товар 101", dto101.getTitle());
//                    assertEquals("img101.png", dto101.getImgPath());
//                    assertEquals(2, dto101.getCount());
//                })
//                .verifyComplete();
//
//        // Проверяем, что в базу данных сходили за всеми сущностями
//        verify(cartRepository, times(1)).findById(cartId);
//        verify(cartItemRepository, times(1)).findByCartId(cartId);
//        verify(productRepository, times(1)).findAllById(any(Iterable.class));
//
//        StepVerifier.create(redisTemplate.hasKey("product:" + prodId1))
//                .expectNext(true)
//                .verifyComplete();
//        StepVerifier.create(redisTemplate.hasKey("product:" + prodId2))
//                .expectNext(true)
//                .verifyComplete();
//
//
//        clearInvocations(cartRepository, cartItemRepository, productRepository);
//
//        Mono<CartDto> secondCall = cartService.getCartDto(cartId);
//
//        StepVerifier.create(secondCall)
//                .assertNext(cartDto -> {
//                    assertNotNull(cartDto);
//                    assertEquals(1200L, cartDto.getTotal());
//                    assertEquals(2, cartDto.getItems().size());
//                })
//                .verifyComplete();
//
//        verify(cartRepository, times(1)).findById(cartId);
//        verify(cartItemRepository, times(1)).findByCartId(cartId);
//
//        verify(productRepository, never()).findAllById(any(Iterable.class));
//
//    }
}
