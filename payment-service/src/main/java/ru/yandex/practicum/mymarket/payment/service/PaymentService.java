package ru.yandex.practicum.mymarket.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.domain.PaymentSuccessResponse;
import ru.yandex.practicum.mymarket.payment.exception.ConflictException;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PaymentService {

    @Value("${app.balance.max-amount:10000}")
    private long maxBalanceAmount;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final ConcurrentHashMap<String, AtomicLong> userBalances = new ConcurrentHashMap<>();

    public PaymentService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<BalanceResponse> getCurrentBalance(String userId) {
        return Mono.fromCallable(() -> {
            AtomicLong balance = userBalances.computeIfAbsent(userId,
                    id -> new AtomicLong(maxBalanceAmount));

            BalanceResponse response = new BalanceResponse();
            response.setAmount(balance.get());
            return response;
        });
    }

    public Mono<PaymentSuccessResponse> chargeIdempotent(String key, String userId, Long amount) {
        String redisKey = "idempotency:payment:" + key;

        return redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofMinutes(5))
                .flatMap(isFirstRequest -> {
                    if (Boolean.TRUE.equals(isFirstRequest)) {
                        return chargeMoney(userId, amount)
                                .flatMap(successResponse ->
                                        redisTemplate.opsForValue().set(redisKey, successResponse, Duration.ofHours(1))
                                                .thenReturn(successResponse)
                                )
                                .onErrorResume(ex ->
                                        redisTemplate.delete(redisKey)
                                                .then(Mono.error(ex))
                                );
                    } else {
                        return redisTemplate.opsForValue().get(redisKey)
                                .flatMap(cachedStatus -> {
                                    if ("PROCESSING".equals(cachedStatus)) {
                                        return Mono.error(new ConflictException(
                                                "Платеж уже обрабатывается. Пожалуйста, подождите."));
                                    }
                                    return Mono.just((PaymentSuccessResponse) cachedStatus);
                                });
                    }
                });
    }


    private Mono<PaymentSuccessResponse> chargeMoney(String userId, Long amount) {
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
                    PaymentSuccessResponse response = new PaymentSuccessResponse();
                    response.setStatus("success");
                    return Mono.just(response);

                } catch (InsufficientFundsException e) {
                    return Mono.error(e);
                }
            });
        }
}
