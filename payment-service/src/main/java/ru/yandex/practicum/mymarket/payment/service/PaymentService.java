package ru.yandex.practicum.mymarket.payment.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    public Mono<BalanceResponse> getCurrentBalance() {
        return Mono.fromCallable(() -> {
            long randomAmount = ThreadLocalRandom.current().nextLong(0, 10001);

            BalanceResponse response = new BalanceResponse();
            response.setAmount(randomAmount);
            return response;
        });
    }

    public Mono<Void> charge(Long amount) {
        return getCurrentBalance()
                .flatMap(balance -> {
                    if (balance.getAmount() == null || balance.getAmount() < amount) {
                        return Mono.error(new InsufficientFundsException("Недостаточно средств"));
                    }
                    return Mono.empty();
                });
    }
}
