package ru.yandex.practicum.mymarket.payment.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class PaymentService {

    @Value("${app.balance.max-amount:10000}")
    private long maxBalanceAmount;

    private final AtomicLong currentBalance = new AtomicLong();

    @PostConstruct
    public void init() {
        currentBalance.set(maxBalanceAmount);
    }

    public Mono<BalanceResponse> getCurrentBalance() {
        return Mono.fromCallable(() -> {
            BalanceResponse response = new BalanceResponse();
            response.setAmount(currentBalance.get());
            return response;
        });
    }

    public Mono<Void> charge(Long amount) {
        return Mono.defer(() -> {
            try {
                currentBalance.updateAndGet(existingBalance -> {
                    if (existingBalance < amount) {
                        throw new InsufficientFundsException("Недостаточно средств");
                    }
                    return existingBalance - amount;
                });

                return Mono.empty();

            } catch (InsufficientFundsException e) {
                return Mono.error(e);
            }
        });
    }
}
