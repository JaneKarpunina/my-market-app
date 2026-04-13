package ru.yandex.practicum.mymarket.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.exception.ProductNotFoundException;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ItemsService.class)
public class ItemsServiceTest extends BaseTest {

    private final Long ITEM_ID = 100L;
    private final String CART_ID = "test-uuid";
    private final String PLUS = "PLUS";
    private final String MINUS = "MINUS";

    @MockitoBean
    private ProductRepository productRepository;

    @Autowired
    private ItemsService itemsService;

    @BeforeEach
    void resetMocks() {
        reset(productRepository);
        reset(cartRepository);
        reset(cartItemRepository);
    }

    @Test
    void test_getItemsWithPaging_emptyItems() {
        when(productRepository.findProductsWithZeroCartId(anyString())).thenReturn(new ArrayList<>());

        ItemsWithPaging result = itemsService.getItemsWithPaging("search", "ALPHA", 1, 10, null);

        verify(productRepository, times(1)).findProductsWithZeroCartId(anyString());
        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        assertFalse(result.getPaging().isHasPrevious());
        assertFalse(result.getPaging().isHasNext());
    }

    @Test
    void test_getItemsWithPaging_someItems_less_than_pageSize() {
        List<ItemDto> mockItems = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            ItemDto item = new ItemDto();
            item.setId(i);
            item.setTitle("Title" + i);
            item.setPrice(i * 100);
            mockItems.add(item);
        }
        when(productRepository.findProductsWithZeroCartId(anyString()))
                .thenReturn(mockItems);

        ItemsWithPaging result = itemsService.getItemsWithPaging("search", "ALPHA", 1, 10, null);

        verify(productRepository, times(1)).findProductsWithZeroCartId(anyString());

        assertEquals(2, result.getItems().size());
        List<List<ItemDto>> rows = result.getItems();
        assertEquals(3, rows.get(0).size()); // должна быть 3 элемента (заполнены до 3)
        assertEquals(-1L, rows.get(1).get(2).getId()); // последний добавленный "заглушка"

        assertFalse(result.getPaging().isHasPrevious());
        assertFalse(result.getPaging().isHasNext());
    }

    @Test
    void test_getItemsWithPaging_multiplePages() {
        List<ItemDto> mockItems = new ArrayList<>();
        for (long i = 1; i <= 25; i++) {
            ItemDto item = new ItemDto();
            item.setId(i);
            item.setTitle("Title" + i);
            item.setPrice(i * 10);
            mockItems.add(item);
        }
        when(productRepository.findProductsWithQuantity(anyString(), anyString()))
                .thenReturn(mockItems);

        // 2-я страница, размер 10
        ItemsWithPaging result = itemsService.getItemsWithPaging("search", "PRICE", 2, 10, "abc");

        verify(productRepository, times(1)).findProductsWithQuantity(anyString(), anyString());

        assertEquals(4, result.getItems().size());
        assertEquals(3, result.getItems().get(0).size()); // каждая строка по 3, последний может быть меньше
        assertEquals(3, result.getItems().get(3).size()); // последний блок (может включать "заглушки")
        assertTrue(result.getPaging().isHasNext());
        assertTrue(result.getPaging().isHasPrevious());
    }

    @Test
    void test_getItemsWithPaging_sorting_by_title() {
        List<ItemDto> mockItems = Arrays.asList(
                createItem(1, "Banana", 200),
                createItem(2, "Apple", 300)
        );
        when(productRepository.findProductsWithQuantity(anyString(), anyString()))
                .thenReturn(mockItems);

        ItemsWithPaging result = itemsService.getItemsWithPaging("search", "ALPHA", 1, 10, "abc");
        List<ItemDto> items = result.getItems().stream()
                .flatMap(List::stream)
                .toList();

        verify(productRepository, times(1)).findProductsWithQuantity(anyString(), anyString());

        assertEquals("Apple", items.get(0).getTitle());
        assertEquals("Banana", items.get(1).getTitle());
    }

    @Test
    void test_getItemsWithPaging_sorting_by_price() {
        List<ItemDto> mockItems = Arrays.asList(
                createItem(1, "Title1", 300),
                createItem(2, "Title2", 100)
        );
        when(productRepository.findProductsWithQuantity(anyString(), anyString()))
                .thenReturn(mockItems);

        ItemsWithPaging result = itemsService.getItemsWithPaging("search", "PRICE", 1, 10, "abc");
        List<ItemDto> items = result.getItems().stream()
                .flatMap(List::stream)
                .toList();

        verify(productRepository, times(1)).findProductsWithQuantity(anyString(), anyString());

        assertTrue(items.get(0).getPrice() <= items.get(1).getPrice());
    }

    private ItemDto createItem(long id, String title, long price) {
        ItemDto item = new ItemDto();
        item.setId(id);
        item.setTitle(title);
        item.setPrice(price);
        return item;
    }

    @Test
    void test_changeItemsCount_NewCart_ShouldSetCookieAndCreateCart() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(productRepository.findById(ITEM_ID)).thenReturn(Optional.of(new Product()));

        itemsService.changeItemsCount(ITEM_ID, PLUS, response, null);

        // Проверяем создание корзины
        verify(cartRepository).save(any(Cart.class));
        verify(cartItemRepository).insertCartItem(anyString(), eq(ITEM_ID), eq(1));

        // Проверяем Cookie
        Cookie cookie = response.getCookie("cartId");
        assertNotNull(cookie);
        assertNotNull(cookie.getValue());
        assertEquals(604800, cookie.getMaxAge()); // 7 дней
    }

    @Test
    void test_changeItemsCount_noProduct() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ProductNotFoundException.class, () ->
                itemsService.changeItemsCount(ITEM_ID, PLUS, response, null));

    }

    @Test
    void test_changeItemsCount_ExistingCart_ShouldIncrementQuantity() {
        cartItemExists(PLUS, 6);
    }

    @Test
    void test_changeItemsCount_QuantityIsOne_MinusShouldDelete() {
        CartItem existingItem = new CartItem(1L, new Cart(), new Product(), 1);

        cartExistsCartItemNotExists(existingItem);

        itemsService.changeItemsCount(ITEM_ID, MINUS, new MockHttpServletResponse(), CART_ID);

        // Если количество 1 и нажали МИНУС — удаляем
        verify(cartItemRepository).delete(existingItem);
        verify(cartItemRepository, never()).updateQuantity(anyLong(), anyInt());
    }

    @Test
    void test_changeItemsCount_existingCartId_ShouldCreateCartAndItem() {

        when(productRepository.findById(ITEM_ID)).thenReturn(Optional.of(new Product()));
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.empty());

        itemsService.changeItemsCount(ITEM_ID, PLUS, new MockHttpServletResponse(), CART_ID);

        verify(cartRepository).save(any());
        verify(cartItemRepository).insertCartItem(CART_ID, ITEM_ID, 1);
    }

    @Test
    void test_changeItemsCount_existingCartId_actionMinus() {

        when(productRepository.findById(ITEM_ID)).thenReturn(Optional.of(new Product()));
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.empty());

        itemsService.changeItemsCount(ITEM_ID, MINUS, new MockHttpServletResponse(), CART_ID);

        verify(cartRepository, never()).save(any());
        verify(cartItemRepository, never()).insertCartItem(CART_ID, ITEM_ID, 1);
    }

    @Test
    void test_changeItemsCount_itemNotFoundInCart_plusShouldInsert() {
        cartExistsCartItemNotExists(null);

        itemsService.changeItemsCount(ITEM_ID, PLUS, new MockHttpServletResponse(), CART_ID);

        // Если товара нет в корзине — создаем запись
        verify(cartItemRepository).insertCartItem(CART_ID, ITEM_ID, 1);
    }

    @Test
    void test_changeItemsCount_itemNotFoundInCart_minusShouldNotInsert() {
        cartExistsCartItemNotExists(null);

        itemsService.changeItemsCount(ITEM_ID, MINUS, new MockHttpServletResponse(), CART_ID);

        // Если товара нет в корзине — создаем запись
        verify(cartItemRepository, never()).insertCartItem(CART_ID, ITEM_ID, 1);
    }

    @Test
    void test_changeItemsCount_ExistingCart_ShouldDecrementQuantity() {
        cartItemExists(MINUS, 4);
    }

    private void cartItemExists(String MINUS, int quantity) {
        CartItem existingItem = new CartItem(1L, new Cart(), new Product(), 5);

        cartExistsCartItemNotExists(existingItem);

        itemsService.changeItemsCount(ITEM_ID, MINUS, new MockHttpServletResponse(), CART_ID);

        // Должен вызвать update с quantity + 1
        verify(cartItemRepository).updateQuantity(existingItem.getId(), quantity);
    }

    private void cartExistsCartItemNotExists(CartItem cartItem) {
        when(productRepository.findById(ITEM_ID)).thenReturn(Optional.of(new Product()));
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(new Cart()));
        when(cartItemRepository.findByCartIdAndProductId(CART_ID, ITEM_ID))
                .thenReturn(Optional.ofNullable(cartItem));
    }

    @Test
    void test_getItemWithQuantity_ShouldThrowException_WhenProductDoesNotExist() {
        when(productRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () ->
                itemsService.getItemWithQuantity(ITEM_ID, CART_ID)
        );

        verify(productRepository, never()).findProductWithQuantity(any(), any());
    }

    @Test
    void test_getItemWithQuantity_WithCartId_ShouldReturnProductWithQuantity() {
        ItemDto mockDto = new ItemDto();
        mockDto.setId(ITEM_ID);
        mockDto.setCount(5);

        when(productRepository.findById(ITEM_ID)).thenReturn(Optional.of(new Product()));
        when(productRepository.findProductWithQuantity(ITEM_ID, CART_ID)).thenReturn(Optional.of(mockDto));

        ItemDto result = itemsService.getItemWithQuantity(ITEM_ID, CART_ID);

        assertNotNull(result);
        assertEquals(5, result.getCount());
        verify(productRepository).findProductWithQuantity(ITEM_ID, CART_ID);
    }

    @Test
    void test_getItemWithQuantity_NoCartId_ShouldReturnProductWithZeroQuantity() {
        ItemDto mockDto = new ItemDto();
        mockDto.setId(ITEM_ID);
        mockDto.setCount(0);

        when(productRepository.findById(ITEM_ID)).thenReturn(Optional.of(new Product()));
        when(productRepository.findProductWithZeroCartId(ITEM_ID)).thenReturn(Optional.of(mockDto));

        ItemDto result = itemsService.getItemWithQuantity(ITEM_ID, null);

        assertNotNull(result);
        assertEquals(0, result.getCount());
        verify(productRepository).findProductWithZeroCartId(ITEM_ID);
    }

    @Test
    void test_getItemWithQuantity_WhenDtoNotFound_ShouldReturnEmptyItemDto() {
        when(productRepository.findById(ITEM_ID)).thenReturn(Optional.of(new Product()));
        when(productRepository.findProductWithZeroCartId(ITEM_ID)).thenReturn(Optional.empty());

        ItemDto result = itemsService.getItemWithQuantity(ITEM_ID, "");

        assertNotNull(result);
        assertNull(result.getId());
    }

}
