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
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;
import ru.yandex.practicum.mymarket.service.ItemsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.reset;

@SpringBootTest
@Import(EmbeddedRedisConfiguration.class)
public class ItemsIntegrationTest {

    @Autowired
    private ItemsService itemService;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private CartRepository cartRepository;

    @MockBean
    private CartItemRepository cartItemRepository;

    @MockBean
    private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;
    @MockBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    private ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    @BeforeEach
    void setUp() {
        redisTemplate.execute(connection -> connection.serverCommands().flushDb()).blockLast();
        reset(productRepository, cartItemRepository, cartRepository);
    }

    @Test
    void getItemsWithPaging_ShouldFetchFromDbAndCacheEverything_WhenRedisIsEmpty() {
        String search = "phone";
        String sort = "title";
        int pageNumber = 1;
        int pageSize = 3;
        Long userId = 42L;
        Long cartId = 100L;

        String expectedPageKey = "page:s:phone:sort:title:p:1:sz:3";

        Mockito.when(productRepository.countByTitleAndDescription(search)).thenReturn(Mono.just(10L));

        Mockito.when(productRepository.findIdsOnly(search, sort, pageSize, 0L))
                .thenReturn(Flux.just(1L, 2L));

        Product p1 = new Product(); p1.setId(1L); p1.setTitle("Iphone 13"); p1.setPrice(50000L);
        Product p2 = new Product(); p2.setId(2L); p2.setTitle("Asus ROG Phone"); p2.setPrice(60000L);
        Mockito.when(productRepository.findAllById(List.of(1L, 2L))).thenReturn(Flux.just(p1, p2));

        Cart mockCart = new Cart(); mockCart.setId(cartId); mockCart.setUserId(userId);
        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));

        CartItem item1 = new CartItem(); item1.setProductId(1L); item1.setQuantity(5); // В корзине 5 шт
        Mockito.when(cartItemRepository.findAllByCartIdAndProductIds(cartId, List.of(1L, 2L)))
                .thenReturn(Flux.just(item1));

        Mono<ItemsWithPaging> result = itemService.getItemsWithPaging(search, sort, pageNumber, pageSize, userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(1, response.getItems().size());

                    List<ItemDto> firstRow = response.getItems().getFirst();
                    assertEquals("Iphone 13", firstRow.get(0).getTitle());
                    assertEquals(5, firstRow.get(0).getCount());

                    assertEquals("Asus ROG Phone", firstRow.get(1).getTitle());
                    assertEquals(0, firstRow.get(1).getCount());

                    assertEquals(-1L, firstRow.get(2).getId());
                })
                .verifyComplete();

        Mono<List<Object>> cachedIdsMono = redisTemplate.opsForList().range(expectedPageKey, 0, -1).collectList();
        StepVerifier.create(cachedIdsMono)
                .assertNext(list -> {
                    assertEquals(2, list.size());
                    assertEquals(1L, ((Number) list.get(0)).longValue());
                    assertEquals(2L, ((Number) list.get(1)).longValue());
                })
                .verifyComplete();

        Mono<Object> cachedProductMono = redisTemplate.opsForValue().get("product:1");
        StepVerifier.create(cachedProductMono)
                .assertNext(obj -> {
                    assertNotNull(obj);
                    Product product = (Product) obj;
                    assertEquals("Iphone 13", product.getTitle());
                })
                .verifyComplete();
    }

    @Test
    void getItemsWithPaging_ShouldReturnDataFromRedisOnly_WhenEverythingIsCached() {
        String search = "samsung";
        String sort = "price";
        int pageNumber = 1;
        int pageSize = 3;
        Long userId = null;

        String expectedPageKey = "page:s:samsung:sort:price:p:1:sz:3";

        Mockito.when(productRepository.countByTitleAndDescription(search)).thenReturn(Mono.just(3L));

        redisTemplate.opsForList().rightPushAll(expectedPageKey, 9L).block();

        Product cachedProduct = new Product();
        cachedProduct.setId(9L);
        cachedProduct.setTitle("Кэшированный Samsung S24");
        cachedProduct.setPrice(90000L);
        redisTemplate.opsForValue().set("product:9", cachedProduct).block();

        Mono<ItemsWithPaging> result = itemService.getItemsWithPaging(search, sort, pageNumber, pageSize, userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    List<ItemDto> row = response.getItems().getFirst();
                    assertEquals("Кэшированный Samsung S24", row.getFirst().getTitle());
                    assertEquals(90000L, row.getFirst().getPrice());
                })
                .verifyComplete();

        Mockito.verify(productRepository, Mockito.never()).findIdsOnly(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyLong());
        Mockito.verify(productRepository, Mockito.never()).findAllById(Mockito.anyCollection());
    }

    @Test
    void getItemWithQuantity_ShouldFetchFromDbAndPopulateRedis_WhenCacheIsEmpty() {
        Long productId = 10L;
        Long userId = 42L;
        Long cartId = 200L;
        String cacheKey = "product:" + productId;

        Product dbProduct = new Product();
        dbProduct.setId(productId);
        dbProduct.setTitle("Планшет из БД");
        dbProduct.setPrice(25000L);
        Mockito.when(productRepository.findById(productId)).thenReturn(Mono.just(dbProduct));

        Cart mockCart = new Cart();
        mockCart.setId(cartId);
        mockCart.setUserId(userId);
        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));

        CartItem mockCartItem = new CartItem();
        mockCartItem.setCartId(cartId);
        mockCartItem.setProductId(productId);
        mockCartItem.setQuantity(4);
        Mockito.when(cartItemRepository.findByCartIdAndProductId(cartId, productId))
                .thenReturn(Mono.just(mockCartItem));

        Mono<ItemDto> result = itemService.getItemWithQuantity(productId, userId);

        StepVerifier.create(result)
                .assertNext(itemDto -> {
                    assertNotNull(itemDto);
                    assertEquals(productId, itemDto.getId());
                    assertEquals("Планшет из БД", itemDto.getTitle());
                    assertEquals(25000L, itemDto.getPrice());
                    assertEquals(4, itemDto.getCount());
                })
                .verifyComplete();

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);

        Mono<Object> redisContentMono = redisTemplate.opsForValue().get(cacheKey);
        StepVerifier.create(redisContentMono)
                .assertNext(obj -> {
                    assertNotNull(obj);
                    Product productInCache = (Product) obj;
                    assertEquals("Планшет из БД", productInCache.getTitle());
                })
                .verifyComplete();
    }

    @Test
    void getItemWithQuantity_ShouldReturnProductFromRedisOnly_WhenCacheExists() {
        Long productId = 20L;
        Long userId = null; // Анонимный пользователь (количество в корзине будет строго 0)
        String cacheKey = "product:" + productId;

        Product cachedProduct = new Product();
        cachedProduct.setId(productId);
        cachedProduct.setTitle("Кэшированные часы");
        cachedProduct.setPrice(15000L);
        redisTemplate.opsForValue().set(cacheKey, cachedProduct).block();

        Mono<ItemDto> result = itemService.getItemWithQuantity(productId, userId);

        StepVerifier.create(result)
                .assertNext(itemDto -> {
                    assertNotNull(itemDto);
                    assertEquals(productId, itemDto.getId());
                    assertEquals("Кэшированные часы", itemDto.getTitle());
                    assertEquals(0, itemDto.getCount()); // Аноним — значит 0
                })
                .verifyComplete();
        Mockito.verify(productRepository, Mockito.never()).findById(Mockito.anyLong());
        Mockito.verify(cartRepository, Mockito.never()).findByUserId(Mockito.anyLong());
    }
}



