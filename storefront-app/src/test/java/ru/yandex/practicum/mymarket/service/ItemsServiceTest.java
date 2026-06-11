package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;

@SpringBootTest(classes = ItemsService.class)
public class ItemsServiceTest extends BaseTest {

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ReactiveListOperations<String, Object> listOperations;

    @MockBean
    private ReactiveValueOperations<String, Object> valueOperations;

    @Autowired
    private ItemsService itemsService;

    @BeforeEach
    void resetMocks() {
        reset(productRepository, cartRepository, cartItemRepository,
                redisTemplate, listOperations, valueOperations);

        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getItemsWithPaging_ShouldReturnDataFromCache_WhenEverythingIsCached() {
        String search = "phone";
        String sort = "title";
        int pageNumber = 1;
        int pageSize = 3;
        Long userId = null;

        String expectedPageKey = "page:s:phone:sort:title:p:1:sz:3";
        List<Long> cachedIds = List.of(10L, 20L);

        Product p1 = new Product();
        p1.setId(10L);
        p1.setTitle("Iphone 13");
        p1.setPrice(50000L);
        Product p2 = new Product();
        p2.setId(20L);
        p2.setTitle("Iphone 14");
        p2.setPrice(60000L);

        Mockito.when(listOperations.range(Mockito.eq(expectedPageKey), Mockito.anyLong(), Mockito.anyLong()))
                .thenReturn(Flux.fromIterable(cachedIds));

        Mockito.when(productRepository.countByTitleAndDescription("phone")).thenReturn(Mono.just(2L));

        Mockito.when(valueOperations.multiGet(List.of("product:10", "product:20")))
                .thenReturn(Mono.just(List.of(p1, p2)));

        Mono<ItemsWithPaging> result = itemsService.getItemsWithPaging(search, sort, pageNumber, pageSize, userId);

        // Верификация
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(1, response.getItems().size());

                    List<ItemDto> row = response.getItems().getFirst();
                    assertEquals("Iphone 13", row.get(0).getTitle());
                    assertEquals("Iphone 14", row.get(1).getTitle());
                    assertEquals(-1L, row.get(2).getId());

                    assertEquals(1, response.getPaging().getPageNumber());
                    assertFalse(response.getPaging().isHasNext());
                    assertFalse(response.getPaging().isHasPrevious());
                })
                .verifyComplete();

        Mockito.verify(productRepository, Mockito.never()).findIdsOnly(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyLong());
        Mockito.verify(productRepository, Mockito.never()).findAllById(Mockito.anyCollection());
    }

    @Test
    void getItemsWithPaging_ShouldFetchFromDbAndPopulateCache_WhenRedisIsEmpty() {
        String search = "tv";
        String sort = "PRICE";
        int pageNumber = 2;
        int pageSize = 3;
        Long userId = 5L;

        String expectedPageKey = "page:s:tv:sort:PRICE:p:2:sz:3";

        Product p1 = new Product();
        p1.setId(100L);
        p1.setTitle("Samsung TV");
        p1.setPrice(30000L);

        Mockito.when(listOperations.range(Mockito.eq(expectedPageKey), Mockito.anyLong(), Mockito.anyLong()))
                .thenReturn(Flux.empty());

        Mockito.when(productRepository.countByTitleAndDescription("tv")).thenReturn(Mono.just(5L));

        Mockito.when(productRepository.findIdsOnly("tv", "PRICE", pageSize, 3L))
                .thenReturn(Flux.just(100L));

        List<Object> multiGetResult = new ArrayList<>(); multiGetResult.add(null);
        Mockito.when(valueOperations.multiGet(List.of("product:100"))).thenReturn(Mono.just(multiGetResult));

        Mockito.when(productRepository.findAllById(List.of(100L))).thenReturn(Flux.just(p1));

        Mockito.when(listOperations.rightPushAll(Mockito.eq(expectedPageKey), Mockito.any(Object[].class))).thenReturn(Mono.just(1L));
        Mockito.when(redisTemplate.expire(Mockito.eq(expectedPageKey), Mockito.any())).thenReturn(Mono.just(true));

        Mockito.when(valueOperations.multiSet(Mockito.anyMap())).thenReturn(Mono.just(true));
        Mockito.when(redisTemplate.expire(Mockito.anyString(), Mockito.any())).thenReturn(Mono.just(true));

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.empty());

        Mono<ItemsWithPaging> result = itemsService.getItemsWithPaging(search, sort, pageNumber, pageSize, userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(2, response.getPaging().getPageNumber());
                    assertTrue(response.getPaging().isHasPrevious());
                    assertFalse(response.getPaging().isHasNext());

                    List<ItemDto> row = response.getItems().getFirst();
                    assertEquals("Samsung TV", row.getFirst().getTitle());
                    assertEquals(0, row.getFirst().getCount());
                })
                .verifyComplete();


        Mockito.verify(listOperations, Mockito.times(1))
                .rightPushAll(Mockito.eq(expectedPageKey), Mockito.any(Object[].class));
        Mockito.verify(valueOperations, Mockito.times(1)).multiSet(Mockito.anyMap());
    }

    @Test
    void getItemsWithPaging_ShouldEnrichProductsWithCartQuantities_WhenUserCartHasItems() {
        String search = "";
        String sort = "title";
        int pageNumber = 1;
        int pageSize = 3;
        Long userId = 77L;

        String expectedPageKey = "page:s::sort:title:p:1:sz:3";

        Product p = new Product();
        p.setId(50L);
        p.setTitle("Наушники");
        p.setPrice(1500L);
        Cart mockCart = new Cart();
        mockCart.setId(999L);
        CartItem mockCartItem = new CartItem();
        mockCartItem.setProductId(50L);
        mockCartItem.setQuantity(3);

        Mockito.when(listOperations.range(Mockito.eq(expectedPageKey),
                Mockito.anyLong(), Mockito.anyLong())).thenReturn(Flux.just(50L));
        Mockito.when(productRepository.countByTitleAndDescription("")).thenReturn(Mono.just(1L));
        Mockito.when(valueOperations.multiGet(List.of("product:50"))).thenReturn(Mono.just(List.of(p)));

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findAllByCartIdAndProductIds(999L, List.of(50L)))
                .thenReturn(Flux.just(mockCartItem));

        Mono<ItemsWithPaging> result = itemsService.getItemsWithPaging(search, sort, pageNumber, pageSize, userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    ItemDto item = response.getItems().getFirst().getFirst();
                    assertEquals("Наушники", item.getTitle());
                    assertEquals(3, item.getCount());
                });
    }

    @Test
    void getItemWithQuantity_ShouldReturnEmptyDto_WhenIdIsNull() {
        Mono<ItemDto> result = itemsService.getItemWithQuantity(null, 1L);

        StepVerifier.create(result)
                .assertNext(itemDto -> {
                    org.junit.jupiter.api.Assertions.assertNull(itemDto.getId());
                    org.junit.jupiter.api.Assertions.assertNull(itemDto.getTitle());
                })
                .verifyComplete();
    }

    @Test
    void getItemWithQuantity_ShouldReturnProductFromCache_WhenCacheExistsAndUserIsAnonymous() {
        Long productId = 10L;
        String cacheKey = "product:" + productId;

        Product cachedProduct = new Product();
        cachedProduct.setId(productId);
        cachedProduct.setTitle("Кэшированный товар");
        cachedProduct.setPrice(1500L);

        Mockito.when(valueOperations.get(cacheKey)).thenReturn(Mono.just(cachedProduct));

        Mono<ItemDto> result = itemsService.getItemWithQuantity(productId, null);

        StepVerifier.create(result)
                .assertNext(itemDto -> {
                    assertNotNull(itemDto);
                    assertEquals(productId, itemDto.getId());
                    assertEquals("Кэшированный товар", itemDto.getTitle());
                    assertEquals(0, itemDto.getCount());
                })
                .verifyComplete();

        Mockito.verify(productRepository, Mockito.never()).findById(Mockito.anyLong());
        Mockito.verify(cartRepository, Mockito.never()).findByUserId(Mockito.anyLong());
    }

    @Test
    void getItemWithQuantity_ShouldFetchFromDbAndCache_WhenRedisCacheIsEmpty() {
        Long productId = 20L;
        Long userId = 77L;
        Long cartId = 300L;
        String cacheKey = "product:" + productId;

        Product dbProduct = new Product();
        dbProduct.setId(productId);
        dbProduct.setTitle("Товар из БД");
        dbProduct.setPrice(4500L);

        Cart mockCart = new Cart();
        mockCart.setId(cartId);

        CartItem mockCartItem = new CartItem();
        mockCartItem.setCartId(cartId);
        mockCartItem.setProductId(productId);
        mockCartItem.setQuantity(6); // 6 штук в корзине у пользователя

        Mockito.when(valueOperations.get(cacheKey)).thenReturn(Mono.empty());

        Mockito.when(productRepository.findById(productId)).thenReturn(Mono.just(dbProduct));

        Mockito.when(valueOperations.set(Mockito.eq(cacheKey), Mockito.any(), Mockito.any())).thenReturn(Mono.just(true));

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(mockCartItem));

        Mono<ItemDto> result = itemsService.getItemWithQuantity(productId, userId);

        StepVerifier.create(result)
                .assertNext(itemDto -> {
                    assertNotNull(itemDto);
                    assertEquals(productId, itemDto.getId());
                    assertEquals("Товар из БД", itemDto.getTitle());
                    assertEquals(6, itemDto.getCount()); // Количество 6 успешно извлечено из cartItem
                })
                .verifyComplete();

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);

        Mockito.verify(valueOperations, Mockito.times(1)).set(Mockito.eq(cacheKey), Mockito.any(), Mockito.any());
    }

}
