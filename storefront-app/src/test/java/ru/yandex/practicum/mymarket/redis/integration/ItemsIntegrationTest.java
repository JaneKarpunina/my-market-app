package ru.yandex.practicum.mymarket.redis.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;
import ru.yandex.practicum.mymarket.service.ItemsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(EmbeddedRedisConfiguration.class)
public class ItemsIntegrationTest {

    @Autowired
    private ItemsService itemsService;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private CartItemRepository cartItemRepository;

    private final String cartId = "test-cart-123";

    @BeforeEach
    void setUp() {
        // Очищаем встроенный Redis перед каждым тестом
        redisTemplate.execute(connection -> connection.serverCommands().flushDb()).blockLast();
        reset(productRepository, cartItemRepository);
    }

    @Test
    void shouldFetchFromDbAndCacheOnFirstCall_ThenReturnFromCacheOnSecondCall() {
        Product product1 = new Product(1L, "Apple iPhone 15", "Description 1",
                "img1.png", 1000L, 1L);
        Product product2 = new Product(2L, "Samsung Galaxy S24",
                "Description 2", "img2.png", 900L, 1L);
        CartItem cartItem1 = new CartItem(10L, cartId, 1L, 2, 1L); // В корзине 2 айфона

        doReturn(Flux.just(1L, 2L)).when(productRepository).findIdsOnly(anyString(), anyString(),
                anyInt(), anyLong());
        doReturn(Mono.just(2L)).when(productRepository).countByTitleAndDescription(anyString());
        doReturn(Flux.just(product1, product2)).when(productRepository).findAllById(any(Iterable.class));
        doReturn(Flux.just(cartItem1)).when(cartItemRepository).findAllByCartIdAndProductIds(anyString(), any(List.class));

        String search = "";
        String sort = "PRICE";
        int pageNumber = 1;
        int pageSize = 5;
        String expectedPageKey = "page:s::sort:PRICE:p:1:sz:5";

        Mono<ItemsWithPaging> firstCall = itemsService.getItemsWithPaging(search, sort, pageNumber, pageSize, cartId);

        StepVerifier.create(firstCall)
                .assertNext(pagingResult -> {
                    assertNotNull(pagingResult);
                    assertEquals(1, pagingResult.getItems().size());
                    List<ItemDto> row = pagingResult.getItems().getFirst();

                    // Первый элемент — iPhone с количеством 2 из корзины
                    assertEquals(1L, row.get(0).getId());
                    assertEquals(2, row.get(0).getCount());

                    // Второй элемент — Samsung, его нет в корзине (количество 0)
                    assertEquals(2L, row.get(1).getId());
                    assertEquals(0, row.get(1).getCount());

                    // Третий элемент — placeholder (-1L), так как размер строки равен 3
                    assertEquals(-1L, row.get(2).getId());
                })
                .verifyComplete();

        verify(productRepository, times(1)).findIdsOnly(anyString(), anyString(),
                anyInt(), anyLong());
        verify(productRepository, times(1)).findAllById(any(Iterable.class));
        verify(cartItemRepository, times(1)).findAllByCartIdAndProductIds(anyString(),
                any(List.class));

        StepVerifier.create(redisTemplate.opsForList().range(expectedPageKey, 0, -1).collectList())
                .assertNext(ids -> {
                    assertEquals(2, ids.size());
                    // Вспоминаем фикс ClassCastException: Jackson может вернуть Integer, приводим типы
                    assertEquals(1, ((Number) ids.get(0)).intValue());
                    assertEquals(2, ((Number) ids.get(1)).intValue());
                })
                .verifyComplete();

        StepVerifier.create(redisTemplate.hasKey("product:1"))
                .expectNext(true)
                .verifyComplete();

        clearInvocations(productRepository, cartItemRepository);

        Mono<ItemsWithPaging> secondCall = itemsService.getItemsWithPaging(search, sort, pageNumber, pageSize, cartId);

        StepVerifier.create(secondCall)
                .assertNext(pagingResult -> {
                    assertNotNull(pagingResult);
                    List<ItemDto> row = pagingResult.getItems().getFirst();
                    assertEquals(1L, row.getFirst().getId());
                    assertEquals(2, row.getFirst().getCount());
                })
                .verifyComplete();

        verify(productRepository, never()).findIdsOnly(anyString(), anyString(), anyInt(), anyLong());
        verify(productRepository, never()).findAllById(any(Iterable.class));

        verify(cartItemRepository, times(1)).findAllByCartIdAndProductIds(anyString(),
                any(List.class));

    }

    @Test
    void shouldFetchProductFromDbAndCacheOnFirstCallThenReturnFromCacheOnSecondCall() {
        Long productId = 42L;
        String productCacheKey = "product:" + productId;

        Product targetProduct = new Product(productId, "Тестовый телефон", "Описание телефона",
                "phone.png", 50000L, 1L);
        CartItem existingCartItem = new CartItem(20L, cartId, productId, 3, 1L); // 3 штуки в корзине

        doReturn(Mono.just(targetProduct)).when(productRepository).findById(productId);
        doReturn(Mono.just(existingCartItem)).when(cartItemRepository).findByCartIdAndProductId(cartId, productId);

        Mono<ItemDto> firstCall = itemsService.getItemWithQuantity(productId, cartId);

        StepVerifier.create(firstCall)
                .assertNext(itemDto -> {
                    assertNotNull(itemDto);
                    assertEquals(productId, itemDto.getId());
                    assertEquals("Тестовый телефон", itemDto.getTitle());
                    assertEquals("phone.png", itemDto.getImgPath());
                    assertEquals(50000L, itemDto.getPrice());
                    assertEquals(3, itemDto.getCount()); // Количество из корзины
                })
                .verifyComplete();

        verify(productRepository, times(1)).findById(productId);
        verify(cartItemRepository, times(1)).findByCartIdAndProductId(cartId, productId);

        StepVerifier.create(redisTemplate.hasKey(productCacheKey))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(redisTemplate.opsForValue().get(productCacheKey).cast(Product.class))
                .assertNext(cachedProduct -> {
                    assertEquals(productId, cachedProduct.getId());
                    assertEquals("Тестовый телефон", cachedProduct.getTitle());
                })
                .verifyComplete();

        clearInvocations(productRepository, cartItemRepository);

        Mono<ItemDto> secondCall = itemsService.getItemWithQuantity(productId, cartId);

        StepVerifier.create(secondCall)
                .assertNext(itemDto -> {
                    assertNotNull(itemDto);
                    assertEquals(productId, itemDto.getId());
                    assertEquals(3, itemDto.getCount()); // Количество всё еще подтягивается
                })
                .verifyComplete();


        verify(productRepository, never()).findById(anyLong());

        verify(cartItemRepository, times(1)).findByCartIdAndProductId(cartId, productId);
    }


}


