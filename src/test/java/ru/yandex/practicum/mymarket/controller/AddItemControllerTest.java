package ru.yandex.practicum.mymarket.controller;

/*import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.service.AddItemService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AddItemController.class)
public class AddItemControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AddItemService addItemService;

    @Test
    void test_addProducts() throws Exception {

        mockMvc.perform(get("/add-item"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"));
    }

    @Test
    void test_addProduct_shouldSaveItemAndRedirect() throws Exception {

        MockMultipartFile imageFile = new MockMultipartFile(
                "image",
                "test.jpg",
                "image/jpeg",
                "some-image-content".getBytes()
        );

        mockMvc.perform(multipart("/item/add")
                        .file(imageFile)
                        .param("title", "Новый товар")
                        .param("description", "Описание товара")
                        .param("price", "1500"))
                .andExpect(status().is3xxRedirection()) // Проверяем редирект
                .andExpect(redirectedUrl("/add-item"))
                .andExpect(flash().attribute("successMessage", "Товар успешно добавлен!"));

        verify(addItemService).addItem(
                eq("Новый товар"),
                eq("Описание товара"),
                eq("1500"),
                any()
        );
    }

}*/
