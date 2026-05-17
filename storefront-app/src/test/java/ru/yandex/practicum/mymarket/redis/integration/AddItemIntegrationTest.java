package ru.yandex.practicum.mymarket.redis.integration;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.ProductRepository;
import ru.yandex.practicum.mymarket.service.AddItemService;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(EmbeddedRedisConfiguration.class)
public class AddItemIntegrationTest {

    @Autowired
    private AddItemService productService;

    @Autowired
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ProductRepository productRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        redisTemplate.execute(connection -> connection.serverCommands().flushDb()).blockLast();
        reset(productRepository);
        ReflectionTestUtils.setField(productService, "imagesDir", tempDir.toString());
    }

    @Test
    void shouldAddItemSaveToDbAndInvalidateAllCatalogPageKeysInRedis() {

        String pageKey1 = "page:s::sort:PRICE:p:1:sz:5";
        String pageKey2 = "page:s:iPhone:sort:ALPHA:p:2:sz:10";
        String otherKey = "product:123"; // Этот ключ НЕ должен удаляться

        redisTemplate.opsForList().rightPushAll(pageKey1, 1L, 2L).block();
        redisTemplate.opsForList().rightPushAll(pageKey2, 3L, 4L).block();
        redisTemplate.opsForValue().set(otherKey, "some-product-data").block();

        List<String> initialKeys = redisTemplate.keys("page:s:*").collectList().block();
        assertEquals(2, initialKeys.size(), "В Redis должно быть 2 ключа пагинации");


        byte[] pngBytes = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82};
        DataBuffer dataBuffer = DefaultDataBufferFactory.sharedInstance.wrap(pngBytes);

        FilePart mockFilePart = mock(FilePart.class);
        when(mockFilePart.filename()).thenReturn("test_image.png");
        when(mockFilePart.content()).thenReturn(Flux.just(dataBuffer));
        when(mockFilePart.transferTo(any(Path.class))).thenReturn(Mono.empty());

        Product savedProduct = new Product(99L, "New Product", "Description",
                "path.png", 500L, 1L);
        doReturn(Mono.just(savedProduct)).when(productRepository).save(any(Product.class));

        Mono<Void> addItemResult = productService.addItem(
                "New Product",
                "Description",
                "500",
                mockFilePart
        );

        StepVerifier.create(addItemResult)
                .verifyComplete();

        verify(productRepository, times(1)).save(any(Product.class));

        StepVerifier.create(redisTemplate.keys("page:s:*").collectList())
                .assertNext(keys -> assertTrue(keys.isEmpty(),
                        "Все ключи пагинации должны быть удалены!"))
                .verifyComplete();

        StepVerifier.create(redisTemplate.hasKey(otherKey))
                .expectNext(true)
                .verifyComplete();
    }
}
