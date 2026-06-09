package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ItemsService.class)
public class ItemsServiceTest extends BaseTest {

    private final Long productId = 1L;
    private final String cartId = "test-cart";
    private final String cacheKey = "product:1";

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ReactiveListOperations<String, Object> listOperations;

    @MockBean
    private ReactiveValueOperations<String, Object> valueOperations;

    @MockBean
    private ServerHttpResponse response;

    @Autowired
    private ItemsService itemsService;

    @BeforeEach
    void resetMocks() {
        reset(productRepository, cartRepository, cartItemRepository,
                redisTemplate, listOperations, valueOperations);

        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

//    @Test
//    void getItemsWithPaging_CacheMiss_ShouldFetchAllFromDbAndWriteToRedis() {
//
//        when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(Flux.empty());
//
//        when(productRepository.findIdsOnly(anyString(), anyString(), anyInt(), anyLong()))
//                .thenReturn(Flux.just(1L, 2L));
//        when(productRepository.countByTitleAndDescription(anyString())).thenReturn(Mono.just(2L));
//
//        when(listOperations.rightPushAll(anyString(), any(Object[].class))).thenReturn(Mono.just(2L));
//        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
//
//        List<Object> cachedObjects = new ArrayList<>();
//        cachedObjects.add(null);
//        cachedObjects.add(null);
//        when(valueOperations.multiGet(anyList())).thenReturn(Mono.just(cachedObjects));
//
//        Product p1 = new Product(1L, "iPhone", "Apple", "img1.png", 1000L, 1L);
//        Product p2 = new Product(2L, "Galaxy", "Samsung", "img2.png", 900L, 1L);
//        when(productRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(p1, p2));
//        when(valueOperations.multiSet(anyMap())).thenReturn(Mono.just(true));
//        CartItem cartItem = new CartItem(5L, cartId, 1L, 1, 1L);
//        when(cartItemRepository.findAllByCartIdAndProductIds(eq(cartId), anyList()))
//                .thenReturn(Flux.just(cartItem));
//
//        Mono<ItemsWithPaging> result = itemsService.getItemsWithPaging("", "PRICE", 1, 5, cartId);
//
//        StepVerifier.create(result)
//                .assertNext(paging -> {
//                    assertNotNull(paging);
//                    assertEquals(1, paging.getItems().size());
//                    List<ItemDto> row = paging.getItems().getFirst();
//
//                    assertEquals(1L, row.get(0).getId());
//                    assertEquals(1, row.get(0).getCount());
//
//                    assertEquals(2L, row.get(1).getId());
//                    assertEquals(0, row.get(1).getCount());
//
//                    assertEquals(-1L, row.get(2).getId());
//                    assertFalse(paging.getPaging().isHasPrevious());
//                    assertFalse(paging.getPaging().isHasNext());
//                    assertEquals(1, paging.getPaging().getPageNumber());
//                })
//                .verifyComplete();
//
//        verify(productRepository, times(1)).findIdsOnly(anyString(), anyString(), anyInt(), anyLong());
//        verify(productRepository, times(1)).countByTitleAndDescription(anyString());
//        verify(productRepository, times(1)).findAllById(any(Iterable.class));
//        verify(cartItemRepository, times(1)).findAllByCartIdAndProductIds(eq(cartId), anyList());
//
//        // Проверяем, что кэш Redis наполнялся
//        verify(listOperations, times(1)).rightPushAll(anyString(), any(Object[].class));
//        verify(valueOperations, times(1)).multiSet(anyMap());
//    }
//
//    @Test
//    void getItemsWithPaging_CacheHit_ShouldFetchEverythingFromRedisExceptCartQuantity() {
//
//        when(listOperations.range(anyString(), anyLong(), anyLong()))
//                .thenReturn(Flux.just(1, 2));
//
//        when(productRepository.countByTitleAndDescription(anyString())).thenReturn(Mono.just(2L));
//
//        Product p1 = new Product(1L, "iPhone", "Apple",
//                "img1.png", 1000L, 1L);
//        Product p2 = new Product(2L, "Galaxy", "Samsung",
//                "img2.png", 900L, 1L);
//        when(valueOperations.multiGet(List.of("product:1", "product:2")))
//                .thenReturn(Mono.just(List.of(p1, p2)));
//
//        CartItem cartItem = new CartItem(6L, cartId, 2L, 5, 1L);
//        when(cartItemRepository.findAllByCartIdAndProductIds(eq(cartId), anyList()))
//                .thenReturn(Flux.just(cartItem));
//
//        Mono<ItemsWithPaging> result = itemsService.getItemsWithPaging("", "ALPHA",
//                1, 5, cartId);
//
//        StepVerifier.create(result)
//                .assertNext(paging -> {
//                    assertNotNull(paging);
//                    List<ItemDto> row = paging.getItems().getFirst();
//
//                    assertEquals(1L, row.get(0).getId());
//                    assertEquals(0, row.get(0).getCount());
//
//                    assertEquals(2L, row.get(1).getId());
//                    assertEquals(5, row.get(1).getCount());
//                })
//                .verifyComplete();
//
//        verify(productRepository, never()).findIdsOnly(anyString(), anyString(), anyInt(), anyLong());
//        verify(productRepository, never()).findAllById(any(Iterable.class));
//        verify(valueOperations, never()).multiSet(anyMap());
//
//        verify(productRepository, times(1)).countByTitleAndDescription(anyString());
//        verify(cartItemRepository, times(1)).findAllByCartIdAndProductIds(eq(cartId), anyList());
//    }
//
//    @Test
//    void getItemWithQuantity_ShouldReturnEmptyDto_WhenIdIsNull() {
//        Mono<ItemDto> result = itemsService.getItemWithQuantity(null, cartId);
//
//        StepVerifier.create(result)
//                .assertNext(itemDto -> {
//                    assertNotNull(itemDto);
//                    assertNull(itemDto.getId());
//                    assertEquals(0, itemDto.getCount());
//                })
//                .verifyComplete();
//
//        verifyNoInteractions(productRepository, cartItemRepository, redisTemplate);
//    }
//
//    @Test
//    void getItemWithQuantity_CacheHit_ShouldReturnProductFromRedis() {
//        Product cachedProduct = new Product(productId, "Кэшированный телефон", "Описание",
//                "phone.png", 50000L, 1L);
//        CartItem cartItem = new CartItem(1L, cartId, productId, 2, 1L);
//
//        when(valueOperations.get(cacheKey)).thenReturn(Mono.just(cachedProduct));
//        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(cartItem));
//
//        Mono<ItemDto> result = itemsService.getItemWithQuantity(productId, cartId);
//
//        StepVerifier.create(result)
//                .assertNext(itemDto -> {
//                    assertNotNull(itemDto);
//                    assertEquals(productId, itemDto.getId());
//                    assertEquals("Кэшированный телефон", itemDto.getTitle());
//                    assertEquals(2, itemDto.getCount()); // Количество из корзины
//                })
//                .verifyComplete();
//
//        verify(productRepository, never()).findById(anyLong());
//        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
//
//        verify(cartItemRepository, times(1)).findByCartIdAndProductId(cartId, productId);
//    }
//
//    @Test
//    void getItemWithQuantity_CacheMiss_ShouldFetchFromDbAndWriteToRedis() {
//        Product dbProduct = new Product(productId, "Телефон из БД",
//                "Описание БД", "db_phone.png", 45000L, 1L);
//        CartItem cartItem = new CartItem(1L, cartId, productId, 5, 1L);
//
//        when(valueOperations.get(cacheKey)).thenReturn(Mono.empty());
//        when(productRepository.findById(productId)).thenReturn(Mono.just(dbProduct));
//        when(valueOperations.set(eq(cacheKey), eq(dbProduct), any(Duration.class))).thenReturn(Mono.just(true));
//
//        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(cartItem));
//
//        Mono<ItemDto> result = itemsService.getItemWithQuantity(productId, cartId);
//
//        StepVerifier.create(result)
//                .assertNext(itemDto -> {
//                    assertNotNull(itemDto);
//                    assertEquals(productId, itemDto.getId());
//                    assertEquals("Телефон из БД", itemDto.getTitle());
//                    assertEquals(5, itemDto.getCount()); // 5 штук
//                })
//                .verifyComplete();
//
//        verify(productRepository, times(1)).findById(productId);
//        verify(valueOperations, times(1)).set(eq(cacheKey), eq(dbProduct), any(Duration.class));
//        verify(cartItemRepository, times(1)).findByCartIdAndProductId(cartId, productId);
//    }
//
//    @Test
//    void getItemWithQuantity_CartIdIdNullOrEmpty_ShouldReturnDtoWithZeroCountWithoutDbQuery() {
//        Product cachedProduct = new Product(productId, "Товар", "Описание",
//                "img.png", 100L, 1L);
//
//        when(valueOperations.get(cacheKey)).thenReturn(Mono.just(cachedProduct));
//
//        Mono<ItemDto> resultEmpty = itemsService.getItemWithQuantity(productId, "");
//        Mono<ItemDto> resultNull = itemsService.getItemWithQuantity(productId, null);
//
//        StepVerifier.create(resultEmpty)
//                .assertNext(itemDto -> assertEquals(0, itemDto.getCount()))
//                .verifyComplete();
//
//        StepVerifier.create(resultNull)
//                .assertNext(itemDto -> assertEquals(0, itemDto.getCount()))
//                .verifyComplete();
//
//        verifyNoInteractions(cartItemRepository);
//    }
//
//    @Test
//    void getItemWithQuantity_ProductNotFound_ShouldReturnEmptyItemDto() {
//        when(valueOperations.get(cacheKey)).thenReturn(Mono.empty());
//        when(productRepository.findById(productId)).thenReturn(Mono.empty());
//
//        Mono<ItemDto> result = itemsService.getItemWithQuantity(productId, cartId);
//
//        StepVerifier.create(result)
//                .assertNext(itemDto -> {
//                    assertNotNull(itemDto);
//                    assertNull(itemDto.getId()); // Отработал defaultIfEmpty(new ItemDto())
//                    assertNull(itemDto.getTitle());
//                })
//                .verifyComplete();
//    }


}
