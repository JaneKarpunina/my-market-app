package ru.yandex.practicum.mymarket.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.api.PaymentApi;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.domain.ErrorResponse;
import ru.yandex.practicum.mymarket.payment.domain.PaymentRequest;
import ru.yandex.practicum.mymarket.payment.domain.PaymentSuccessResponse;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;
import ru.yandex.practicum.mymarket.payment.service.PaymentService;

@RestController
public class PaymentController implements PaymentApi {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public Mono<ResponseEntity<BalanceResponse>> getBalance(ServerWebExchange exchange) {
        return paymentService.getCurrentBalance()
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<PaymentSuccessResponse>> processPayment(
            Mono<PaymentRequest> paymentRequest,
            ServerWebExchange exchange) {

        return paymentRequest
                .flatMap(request -> paymentService.charge(request.getAmount()))
                .then(Mono.just(ResponseEntity.ok(new PaymentSuccessResponse().status("success"))));

    }
}

