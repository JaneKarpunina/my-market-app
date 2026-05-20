package ru.yandex.practicum.mymarket.payment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.domain.PaymentRequest;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payment.service.PaymentService;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(PaymentController.class)
public class PaymentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PaymentService paymentService;

    @Test
    void shouldReturnCurrentBalance() {
        BalanceResponse mockBalance = new BalanceResponse();
        mockBalance.setAmount(5000L);

        when(paymentService.getCurrentBalance()).thenReturn(Mono.just(mockBalance));

        webTestClient.get()
                .uri("/api/v1/balance")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.amount").isEqualTo(5000);

        verify(paymentService).getCurrentBalance();
    }

    @Test
    void shouldProcessPaymentSuccessfully() {
        PaymentRequest mockRequest = new PaymentRequest();
        mockRequest.setAmount(1500L);

        when(paymentService.charge(1500L)).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/api/v1/payment") // Скорректируйте путь для создания платежа
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mockRequest)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("success");

        verify(paymentService).charge(1500L);
    }

    @Test
    void shouldReturnErrorWhenPaymentFails() {
        PaymentRequest mockRequest = new PaymentRequest();
        mockRequest.setAmount(99999L);

        when(paymentService.charge(anyLong()))
                .thenReturn(Mono.error(new InsufficientFundsException("Недостаточно средств")));

        webTestClient.post()
                .uri("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mockRequest)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
