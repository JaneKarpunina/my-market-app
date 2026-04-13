package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.service.ItemsService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AllItemsController.class)
public class AllItemsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ItemsService itemsService;

    @Test
    void test_getItems_shouldReturnItemsViewWithCorrectModel() throws Exception {
        Paging mockPaging = new Paging(5, 1, false, true);
        List<List<ItemDto>> mockRows = List.of(List.of(new ItemDto()));
        ItemsWithPaging mockResponse = new ItemsWithPaging(mockRows, mockPaging);

        when(itemsService.getItemsWithPaging(anyString(), anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/items")
                        .param("search", "phone")
                        .param("sort", "PRICE")
                        .param("pageNumber", "1")
                        .cookie(new Cookie("cartId", "some-uuid")))
                .andExpect(status().isOk())
                .andExpect(view().name("items"))
                .andExpect(model().attributeExists("items", "paging", "search", "sort"))
                .andExpect(model().attribute("search", "phone"))
                .andExpect(model().attribute("sort", "PRICE"));

        verify(itemsService).getItemsWithPaging(anyString(), anyString(), anyInt(), anyInt(), any());

    }

    @Test
    void test_getItems_withoutParams_shouldUseDefaultValues() throws Exception {
        ItemsWithPaging mockResponse = new ItemsWithPaging(List.of(), new Paging(5, 1, false, false));

        when(itemsService.getItemsWithPaging(null, "NO", 1, 5, null))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("items", "paging", "sort"))
                .andExpect(model().attribute("sort", "NO"));

        verify(itemsService).getItemsWithPaging(null, "NO", 1, 5, null);
    }


}
