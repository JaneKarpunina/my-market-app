package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.ItemsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemsController.class)
public class ItemsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ItemsService itemsService;

    @Test
    void test_changeItemQuantity_shouldInvokeServiceAndRedirectWithParams() throws Exception {

        Long id = 1L;
        String action = "PLUS";
        String search = "java";
        String sort = "PRICE";
        int page = 2;
        int size = 10;
        String cartId = "uuid-123";

        mockMvc.perform(post("/items")
                        .param("id", id.toString())
                        .param("action", action)
                        .param("search", search)
                        .param("sort", sort)
                        .param("pageNumber", String.valueOf(page))
                        .param("pageSize", String.valueOf(size))
                        .cookie(new Cookie("cartId", cartId)))
                .andExpect(status().is3xxRedirection());

        verify(itemsService).changeItemsCount(
                eq(id),
                eq(action),
                any(HttpServletResponse.class),
                eq(cartId)
        );
    }

    @Test
    void test_changeItemQuantity_withDefaults_shouldRedirectWithDefaultParams() throws Exception {
        // Тест на значения по умолчанию (search="", sort=NO, page=1, size=5)
        mockMvc.perform(post("/items")
                        .param("id", "5")
                        .param("action", "MINUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/items?search=&sort=NO&pageNumber=1&pageSize=5"));

        verify(itemsService).changeItemsCount(eq(5L), eq("MINUS"), any(), any());
    }

    @Test
    void test_getItem_shouldReturnItemViewWithCorrectModel() throws Exception {
        // Подготовка данных
        Long itemId = 1L;
        String cartId = "cart-123";
        ItemDto mockItem = new ItemDto();
        mockItem.setId(itemId);
        mockItem.setTitle("Тестовый товар");
        mockItem.setCount(2);

        when(itemsService.getItemWithQuantity(itemId, cartId)).thenReturn(mockItem);

        mockMvc.perform(get("/items/{id}", itemId)
                        .cookie(new Cookie("cartId", cartId)))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attribute("item", mockItem))
                .andExpect(model().attributeExists("item"));
    }

    @Test
    void test_getItem_withoutCookie_shouldStillReturnView() throws Exception {
        Long itemId = 99L;
        ItemDto mockItem = new ItemDto();
        mockItem.setId(itemId);

        when(itemsService.getItemWithQuantity(itemId, null)).thenReturn(mockItem);

        mockMvc.perform(get("/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attribute("item", mockItem));
    }

    @Test
    void test_changeItemQuantity_shouldUpdateAndReturnItemView() throws Exception {
        // Данные
        Long itemId = 1L;
        String action = "PLUS";
        String cartId = "uuid-abc";
        ItemDto updatedItem = new ItemDto();
        updatedItem.setId(itemId);
        updatedItem.setCount(5);

        doNothing().when(itemsService).changeItemsCount(eq(itemId), eq(action), any(HttpServletResponse.class), eq(cartId));
        when(itemsService.getItemWithQuantity(itemId, cartId)).thenReturn(updatedItem);

        // Выполнение запроса
        mockMvc.perform(post("/items/{id}", itemId)
                        .param("action", action)
                        .cookie(new Cookie("cartId", cartId)))
                .andExpect(status().isOk())
                .andExpect(view().name("item"))
                .andExpect(model().attribute("item", updatedItem));

        verify(itemsService).changeItemsCount(eq(itemId), eq(action), any(), eq(cartId));
        verify(itemsService).getItemWithQuantity(itemId, cartId);

    }

    @Test
    void test_changeItemQuantity_withoutCookie() throws Exception {
        Long itemId = 2L;
        ItemDto item = new ItemDto();

        when(itemsService.getItemWithQuantity(itemId, null)).thenReturn(item);

        mockMvc.perform(post("/items/{id}", itemId)
                        .param("action", "MINUS"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("item"));

        verify(itemsService).changeItemsCount(eq(itemId), eq("MINUS"), any(), isNull());
    }
}
