package ru.yandex.practicum.mymarket.payment.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PaymentService {

    @Value("${app.balance.max-amount:10000}")
    private long maxBalanceAmount;

    private final ConcurrentHashMap<String, AtomicLong> userBalances = new ConcurrentHashMap<>();

    public Mono<BalanceResponse> getCurrentBalance(String userId) {
        return Mono.fromCallable(() -> {
            AtomicLong balance = userBalances.computeIfAbsent(userId,
                    id -> new AtomicLong(maxBalanceAmount));

            BalanceResponse response = new BalanceResponse();
            response.setAmount(balance.get());
            return response;
        });
    }

    public Mono<Void> charge(String userId, Long amount) {
        return Mono.defer(() -> {
            AtomicLong balance = userBalances.computeIfAbsent(userId,
                    id -> new AtomicLong(maxBalanceAmount));
            try {
                balance.updateAndGet(existingBalance -> {
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
