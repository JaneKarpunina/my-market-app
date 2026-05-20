package ru.yandex.practicum.mymarket.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    @Value("${app.balance.max-amount:10000}")
    private long maxBalanceAmount;

    public Mono<BalanceResponse> getCurrentBalance() {
        return Mono.fromCallable(() -> {
            long randomAmount = ThreadLocalRandom.current().nextLong(0, maxBalanceAmount + 1);

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
