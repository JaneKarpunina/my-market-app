package ru.yandex.practicum.mymarket.redis.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;
import ru.yandex.practicum.mymarket.service.CartService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.reset;

@SpringBootTest
@Import(EmbeddedRedisConfiguration.class)
public class CartIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private CartRepository cartRepository;

    @MockBean
    private CartItemRepository cartItemRepository;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;
    @MockBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    private ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    @BeforeEach
    void cleanUpRedis() {
        redisTemplate.execute(connection -> connection.serverCommands().flushDb()).blockLast();
        reset(productRepository, cartItemRepository, cartRepository);
    }

    private void givenCartAndItemsExistInDb(Long userId, Long cartId, Long productId, int quantity) {
        Cart mockCart = new Cart();
        mockCart.setId(cartId);
        mockCart.setUserId(userId);
        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));

        CartItem mockItem = new CartItem();
        mockItem.setCartId(cartId);
        mockItem.setProductId(productId);
        mockItem.setQuantity(quantity);
        Mockito.when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(mockItem));
    }

    @Test
    void getCartDto_ShouldReturnProductsFromRedisCache_WhenCacheExists() {
        Long userId = 1L;
        Long cartId = 100L;
        Long productId = 500L;
        int quantity = 2;

        givenCartAndItemsExistInDb(userId, cartId, productId, quantity);

        Product cachedProduct = new Product();
        cachedProduct.setId(productId);
        cachedProduct.setTitle("Кэшированный телефон");
        cachedProduct.setPrice(1000L);
        redisTemplate.opsForValue().set("product:" + productId, cachedProduct).block();

        Mono<CartDto> result = cartService.getCartDto(userId);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    // Используем стандартные JUnit 5 Assertions вместо AssertJ
                    assertEquals(2000L, cartDto.getTotal()); // 1000 * 2
                    assertEquals(1, cartDto.getItems().size());
                    assertEquals("Кэшированный телефон", cartDto.getItems().getFirst().getTitle());
                })
                .verifyComplete();

        Mockito.verify(productRepository, Mockito.never()).findAllById(Mockito.anyCollection());
    }

    @Test
    void getCartDto_ShouldFetchFromDbAndPopulateRedis_WhenCacheIsEmpty() {
        Long userId = 1L;
        Long cartId = 100L;
        Long productId = 700L;
        int quantity = 3;

        givenCartAndItemsExistInDb(userId, cartId, productId, quantity);

        Product dbProduct = new Product();
        dbProduct.setId(productId);
        dbProduct.setTitle("Ноутбук из БД");
        dbProduct.setPrice(5000L);
        Mockito.when(productRepository.findAllById(List.of(productId))).thenReturn(Flux.just(dbProduct));

        Mono<CartDto> result = cartService.getCartDto(userId);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertEquals(15000L, cartDto.getTotal()); // 5000 * 3
                    assertEquals("Ноутбук из БД", cartDto.getItems().get(0).getTitle());
                })
                .verifyComplete();

        Mockito.verify(productRepository, Mockito.times(1)).findAllById(List.of(productId));

        Mono<Object> redisContentMono = redisTemplate.opsForValue().get("product:" + productId);

        StepVerifier.create(redisContentMono)
                .assertNext(obj -> {
                    assertNotNull(obj);
                    Product productInCache = (Product) obj;
                    assertEquals("Ноутбук из БД", productInCache.getTitle());
                    assertEquals(5000L, productInCache.getPrice());
                })
                .verifyComplete();
    }

}
