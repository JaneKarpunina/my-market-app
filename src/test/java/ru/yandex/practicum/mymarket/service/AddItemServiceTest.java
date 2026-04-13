package ru.yandex.practicum.mymarket.service;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.yandex.practicum.mymarket.exception.NumberOutsideOfRangeException;
import ru.yandex.practicum.mymarket.exception.UnsupportedMediaTypeException;
import ru.yandex.practicum.mymarket.repository.ProductRepository;
import ru.yandex.practicum.mymarket.utils.Utils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AddItemService.class)
public class AddItemServiceTest {

    @MockitoBean
    private ProductRepository productRepository;

    @Autowired
    private AddItemService addItemService;

    @BeforeEach
    void resetMocks() {
        reset(productRepository);
    }

    @AfterAll
    public static void deleteDirectory() throws IOException {
        Utils.deleteDirectory("uploads/");
    }

    @Test
    void testAddItem_success() {

        String title = "Тест";
        String description = "Описание";
        String price = "100";

        MockMultipartFile image = getMockMultipartFile();

        addItemService.addItem(title, description, price, image);

        verify(productRepository, times(1)).save(any());

    }

    @Test
    void testAddItem_invalidFileType() {
        MockMultipartFile image = new MockMultipartFile("imgUrl",
                "image-file.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "some text".getBytes());

        assertThrows(UnsupportedMediaTypeException.class, () ->
                addItemService.addItem("title", "desc", "50", image));
    }

    @Test
    void testAddItem_invalidPrice_negative() {

        testNumberOutsideOfRangeException("-100");


    }

    @Test
    void testAddItem_invalidPrice_maxExceeded() {

        testNumberOutsideOfRangeException("10000000000000000000");


    }

    private void testNumberOutsideOfRangeException(String price) {
        String title = "Тест";
        String description = "Описание";

        MockMultipartFile image = getMockMultipartFile();

        assertThrows(NumberOutsideOfRangeException.class, () ->
                addItemService.addItem(title, description, price, image));
    }

    @Test
    void testAddItem_invalidPrice_notLongValue() {

        String title = "Тест";
        String description = "Описание";
        String price = "abc";

        MockMultipartFile image = getMockMultipartFile();

        assertThrows(IllegalArgumentException.class, () ->
                addItemService.addItem(title, description, price, image));


    }

    private MockMultipartFile getMockMultipartFile() {
        byte[] jpegBytes = new byte[] {
                (byte)0xFF, (byte)0xD8, // SOI (Start of Image)
                (byte)0xFF, (byte)0xD9 // EOI (End of Image)
        };
        return new MockMultipartFile("imgUrl",
                "image-file.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                jpegBytes);
    }
}
