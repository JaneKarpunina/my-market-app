package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.AddItemService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(AddItemController.class)
public class AddItemControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    AddItemService addItemService;

    @Test
    void addProducts_ReturnView() {
        webTestClient.get()
                .uri("/add-item?success=false")
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    @Test
    void addProduct_Success_Redirect() {

        when(addItemService.addItem(anyString(), anyString(), anyString(), any(FilePart.class)))
                .thenReturn(Mono.empty());

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("title", "Sony TV");
        bodyBuilder.part("description", "Best 4K TV");
        bodyBuilder.part("price", "50000");
        bodyBuilder.part("image", "fake-image-content".getBytes())
                .filename("test.jpg")
                .contentType(MediaType.IMAGE_JPEG);

        webTestClient.post()
                .uri("/item/add")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().is3xxRedirection() // Проверяем редирект
                .expectHeader().valueEquals("Location", "/add-item?success=true");
    }



}
