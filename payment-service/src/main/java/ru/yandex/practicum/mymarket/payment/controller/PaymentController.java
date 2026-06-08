package ru.yandex.practicum.mymarket.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.api.PaymentApi;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.domain.PaymentRequest;
import ru.yandex.practicum.mymarket.payment.domain.PaymentSuccessResponse;
import ru.yandex.practicum.mymarket.payment.service.PaymentService;

@RestController
public class PaymentController implements PaymentApi {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public Mono<ResponseEntity<BalanceResponse>> getBalance(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

        return paymentService.getCurrentBalance(userId)
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<PaymentSuccessResponse>> processPayment(
            Mono<PaymentRequest> paymentRequest,
            ServerWebExchange exchange) {

        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

        return paymentRequest
                .flatMap(request -> paymentService.charge(userId, request.getAmount()))
                .then(Mono.just(ResponseEntity.ok(new PaymentSuccessResponse().status("success"))));

    }
}

