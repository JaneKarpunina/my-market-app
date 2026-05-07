package ru.yandex.practicum.mymarket.service;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.exception.NumberOutsideOfRangeException;
import ru.yandex.practicum.mymarket.exception.UnsupportedMediaTypeException;
import ru.yandex.practicum.mymarket.repository.ProductRepository;
import ru.yandex.practicum.mymarket.utils.Utils;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AddItemService.class)
public class AddItemServiceTest {

    public static final String TITLE = "title";
    public static final String DESC = "desc";
    public static final String PRICE = "100";
    @MockitoBean
    private ProductRepository productRepository;

    @Autowired
    private AddItemService addItemService;

    private FilePart mockFile;

    @BeforeEach
    void resetMocks() {
        reset(productRepository);
        mockFile = mock(FilePart.class);
    }

    @AfterAll
    public static void deleteDirectory() throws IOException {
        Utils.deleteDirectory("uploads/");
    }

    @Test
    void addItem_Success() {
        byte[] pngSignature = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(pngSignature);

        when(mockFile.filename()).thenReturn("image.png");
        when(mockFile.content()).thenReturn(Flux.just(buffer));
        when(mockFile.transferTo(any(Path.class))).thenReturn(Mono.empty());
        when(productRepository.save(any())).thenReturn(Mono.just(new Product()));

        StepVerifier.create(addItemService.addItem(TITLE, DESC, PRICE, mockFile))
                .verifyComplete();

        verify(productRepository, times(1)).save(any());
    }

    @Test
    void addItem_InvalidPrice_FormatError() {
        StepVerifier.create(addItemService.addItem(TITLE, DESC, "abc", mockFile))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(productRepository);
    }

    @Test
    void addItem_PriceOutOfRange() {
        String hugePrice = "999999999999999999999999999";

        StepVerifier.create(addItemService.addItem(TITLE, DESC, hugePrice, mockFile))
                .expectError(NumberOutsideOfRangeException.class)
                .verify();
    }

    @Test
    void addItem_FileTooLarge() {
        // Эмулируем файл размером больше MAX_FILE_SIZE
        byte[] bigContent = new byte[1024 * 1024 * 10]; // 10MB
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(bigContent);

        when(mockFile.filename()).thenReturn("large.jpg");
        when(mockFile.content()).thenReturn(Flux.just(buffer));

        StepVerifier.create(addItemService.addItem(TITLE, DESC, PRICE, mockFile))
                .expectError(MaxUploadSizeExceededException.class)
                .verify();
    }

    @Test
    void addItem_InvalidFileType() {
        // Эмулируем текстовый файл вместо картинки
        byte[] textContent = "This is a simple text file content".getBytes();
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(textContent);

        when(mockFile.filename()).thenReturn("test.txt");
        when(mockFile.content()).thenReturn(Flux.just(buffer));

        StepVerifier.create(addItemService.addItem(TITLE, DESC, PRICE, mockFile))
                .expectError(UnsupportedMediaTypeException.class)
                .verify();
    }
}
