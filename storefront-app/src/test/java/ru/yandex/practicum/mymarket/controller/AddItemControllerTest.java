package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.config.SecurityConfig;
import ru.yandex.practicum.mymarket.service.AddItemService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AddItemControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    AddItemService addItemService;

    @MockBean
    private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;
    @MockBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    private ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    @Test
    void addProducts_ShouldReturn403Forbidden_WhenUserIsAnonymous() {
        webTestClient.get()
                .uri("/add-item")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @WithMockUser
    void addProducts_ShouldReturnTemplateAndModel_WhenUserIsAuthenticated() {
        webTestClient.get()
                .uri("/add-item?success=true")
                .exchange()
                .expectStatus().isOk();

    }

    @Test
    @WithMockUser
    void addProduct_ShouldCallServiceAndRedirect_WhenValidMultipartData() {

        Mockito.when(addItemService.addItem(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(FilePart.class)
        )).thenReturn(Mono.empty());

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("title", "Смартфон");
        bodyBuilder.part("description", "Флагманский телефон");
        bodyBuilder.part("price", "75000");

        bodyBuilder.part("image", "содержимое_файла".getBytes())
                .filename("test.jpg")
                .contentType(MediaType.IMAGE_JPEG);

        MultiValueMap<String, HttpEntity<?>> multipartBody = bodyBuilder.build();

        webTestClient.post()
                .uri("/item/add")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(multipartBody)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueEquals("Location", "/add-item?success=true");

        Mockito.verify(addItemService, Mockito.times(1))
                .addItem(
                        Mockito.eq("Смартфон"),
                        Mockito.eq("Флагманский телефон"),
                        Mockito.eq("75000"),
                        Mockito.any(FilePart.class)
                );
    }



}
