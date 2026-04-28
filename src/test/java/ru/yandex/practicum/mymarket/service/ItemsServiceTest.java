package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ItemsService.class)
public class ItemsServiceTest extends BaseTest {

    private final Long productId = 1L;
    private final String cartId = "test-cart";

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private ServerHttpResponse response;

    @Autowired
    private ItemsService itemsService;

    @BeforeEach
    void resetMocks() {
        reset(productRepository);
        reset(cartRepository);
        reset(cartItemRepository);
    }

    @Test
    void getItemsWithPaging_Success() {
        String search = "phone";
        String cartId = "cart-123";
        int page = 1;
        int size = 3;

        ItemDto item1 = new ItemDto();
        item1.setId(1L);
        ItemDto item2 = new ItemDto();
        item2.setId(2L);
        // Всего 2 товара, значит должна быть 1 строка, дополненная 1 пустышкой

        when(productRepository.findProductsWithQuantityPaged(anyString(), anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(Flux.just(item1, item2));

        when(productRepository.countByTitleAndDescription(anyString()))
                .thenReturn(Mono.just(2L));

        Mono<ItemsWithPaging> result = itemsService.getItemsWithPaging(search, "asc", page, size, cartId);

        StepVerifier.create(result)
                .assertNext(pagingResponse -> {
                    // Проверка пагинации
                    assertNotNull(pagingResponse.getPaging());
                    assertEquals(1, pagingResponse.getPaging().getPageNumber());
                    assertFalse(pagingResponse.getPaging().isHasNext());

                    // Проверка разбиения на строки (partitionAndFill)
                    List<List<ItemDto>> rows = pagingResponse.getItems();
                    assertEquals(1, rows.size());
                    assertEquals(3, rows.getFirst().size());
                    assertEquals(-1L, rows.getFirst().get(2).getId());
                })
                .verifyComplete();
    }

    @Test
    void getItemsWithPaging_NoCartId() {
        when(productRepository.findProductsWithZeroCartIdPaged(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(productRepository.countByTitleAndDescription(anyString()))
                .thenReturn(Mono.just(0L));

        Mono<ItemsWithPaging> result = itemsService.getItemsWithPaging(null, "desc",
                1, 3, null);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertTrue(response.getItems().isEmpty());
                    assertEquals(1, response.getPaging().getPageNumber());
                })
                .verifyComplete();
    }

    @Test
    void getItemsWithPaging_MultiplePages() {

        int pageSize = 3;
        long totalItems = 10; // 10 товаров / 3 на стр = 4 страницы

        when(productRepository.findProductsWithZeroCartIdPaged(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(Flux.just(new ItemDto(), new ItemDto(), new ItemDto()));
        when(productRepository.countByTitleAndDescription(anyString()))
                .thenReturn(Mono.just(totalItems));

        Mono<ItemsWithPaging> result = itemsService.getItemsWithPaging("", "asc", 2,
                pageSize, null);

        StepVerifier.create(result)
                .assertNext(response -> {
                    Paging p = response.getPaging();
                    assertEquals(2, p.getPageNumber());
                    assertTrue(p.isHasPrevious());
                    assertTrue(p.isHasNext());
                })
                .verifyComplete();
    }

    @Test
    void changeItemsCount_NewCartCreation() {
        when(productRepository.findById(productId)).thenReturn(Mono.just(new Product()));
        when(cartRepository.save(any(Cart.class))).thenReturn(Mono.just(new Cart()));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(Mono.just(new CartItem()));

        when(response.getCookies()).thenReturn(new LinkedMultiValueMap<>());

        StepVerifier.create(itemsService.changeItemsCount(productId, "PLUS", response, null))
                .verifyComplete();

        verify(cartRepository).save(any(Cart.class));
        verify(cartItemRepository).save(any(CartItem.class));
        verify(response).addCookie(any(ResponseCookie.class));
    }

    @Test
    void changeItemsCount_IncrementExistingItem() {
        CartItem existingItem = new CartItem(10L, cartId, productId, 1, 0L);

        when(productRepository.findById(productId)).thenReturn(Mono.just(new Product()));
        when(cartRepository.findById(cartId)).thenReturn(Mono.just(new Cart()));
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(existingItem));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(Mono.just(existingItem));

        StepVerifier.create(itemsService.changeItemsCount(productId, "PLUS", response, cartId))
                .verifyComplete();

        verify(cartItemRepository).save(argThat(item -> item.getQuantity() == 2));
    }

    @Test
    void changeItemsCount_CartNotFoundInDb_CreateNew() {
        String existingCartId = "expired-cart-id";

        when(productRepository.findById(productId)).thenReturn(Mono.just(new Product()));
        when(cartRepository.findById(existingCartId)).thenReturn(Mono.empty());

        when(cartRepository.save(any(Cart.class))).thenReturn(Mono.just(new Cart()));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(Mono.just(new CartItem()));

        StepVerifier.create(itemsService.changeItemsCount(productId, "PLUS", response, existingCartId))
                .verifyComplete();

        verify(cartRepository).save(argThat(cart -> cart.getId().equals(existingCartId)));
        verify(cartItemRepository).save(argThat(item -> item.getProductId().equals(productId)));


        verify(response, times(0)).addCookie(any());
    }

    @Test
    void changeItemsCount_RemoveItemOnMinus() {

        CartItem existingItem = new CartItem(10L, cartId, productId, 1, 0L);

        when(productRepository.findById(productId)).thenReturn(Mono.just(new Product()));
        when(cartRepository.findById(cartId)).thenReturn(Mono.just(new Cart()));
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(existingItem));
        when(cartItemRepository.delete(existingItem)).thenReturn(Mono.empty());

        StepVerifier.create(itemsService.changeItemsCount(productId, "MINUS", response, cartId))
                .verifyComplete();

        verify(cartItemRepository).delete(existingItem);
    }

    @Test
    void changeItemsCount_ProductNotFound() {

        when(productRepository.findById(productId)).thenReturn(Mono.empty());

        StepVerifier.create(itemsService.changeItemsCount(productId, "PLUS", response, cartId))
                .verifyComplete();

        verifyNoInteractions(cartRepository);
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void changeItemsCount_AddItemToExistingCartViaSwitch() {

        when(productRepository.findById(productId)).thenReturn(Mono.just(new Product()));
        when(cartRepository.findById(cartId)).thenReturn(Mono.just(new Cart()));

        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(Mono.just(new CartItem()));

        StepVerifier.create(itemsService.changeItemsCount(productId, "PLUS", response, cartId))
                .verifyComplete();

        verify(cartItemRepository).save(argThat(item -> item.getQuantity() == 1));
    }

    @Test
    void changeItemsCount_DecrementExistingItem() {

        int initialQuantity = 5;
        CartItem existingItem = new CartItem(10L, cartId, productId, initialQuantity, 0L);

        when(productRepository.findById(productId)).thenReturn(Mono.just(new Product()));
        when(cartRepository.findById(cartId)).thenReturn(Mono.just(new Cart()));
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId))
                .thenReturn(Mono.just(existingItem));

        when(cartItemRepository.save(any(CartItem.class))).thenReturn(Mono.just(existingItem));

        StepVerifier.create(itemsService.changeItemsCount(productId, "MINUS", response, cartId))
                .verifyComplete();

        verify(cartItemRepository).save(argThat(item ->
                item.getQuantity() == (initialQuantity - 1) && item.getProductId().equals(productId)
        ));

        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void getItemWithQuantity_ProductNotFound() {

        when(productRepository.findById(productId)).thenReturn(Mono.empty());

        Mono<ItemDto> result = itemsService.getItemWithQuantity(productId, cartId);

        StepVerifier.create(result)
                .verifyComplete();

        verify(productRepository, times(1)).findById(productId);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void getItemWithQuantity_NoCartId() {

        ItemDto expectedDto = new ItemDto();
        expectedDto.setId(productId);
        expectedDto.setTitle("Product 1");
        expectedDto.setPrice( 100L);
        expectedDto.setCount(0);

        when(productRepository.findById(productId)).thenReturn(Mono.just(new Product()));
        when(productRepository.findProductWithZeroCartId(productId)).thenReturn(Mono.just(expectedDto));

        Mono<ItemDto> result = itemsService.getItemWithQuantity(productId, "");

        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertEquals(productId, dto.getId());
                    assertEquals(0, dto.getCount());
                })
                .verifyComplete();

        verify(productRepository).findProductWithZeroCartId(productId);
        verify(productRepository, never()).findProductWithQuantity(anyLong(), anyString());
    }

    @Test
    void getItemWithQuantity_WithCartId() {

        ItemDto expectedDto = new ItemDto();
        expectedDto.setId(productId);
        expectedDto.setTitle("Product 1");
        expectedDto.setPrice( 100L);
        expectedDto.setCount(5);

        when(productRepository.findById(productId)).thenReturn(Mono.just(new Product()));
        when(productRepository.findProductWithQuantity(productId, cartId)).thenReturn(Mono.just(expectedDto));

        Mono<ItemDto> result = itemsService.getItemWithQuantity(productId, cartId);

        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertEquals(productId, dto.getId());
                    assertEquals(5, dto.getCount());
                })
                .verifyComplete();

        verify(productRepository).findProductWithQuantity(productId, cartId);
    }

    @Test
    void getItemWithQuantity_DetailsNotFound() {
        when(productRepository.findById(productId)).thenReturn(Mono.just(new Product()));
        when(productRepository.findProductWithQuantity(productId, cartId)).thenReturn(Mono.empty());

        Mono<ItemDto> result = itemsService.getItemWithQuantity(productId, cartId);

        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertNull(dto.getId());
                })
                .verifyComplete();
    }

}
