package ru.yandex.practicum.mymarket.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.domain.PaymentSuccessResponse;
import ru.yandex.practicum.mymarket.payment.exception.ConflictException;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PaymentService.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class BalanceServiceTest {

    @Autowired
    private PaymentService paymentService;

    @MockBean
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @MockBean
    private ReactiveValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "maxBalanceAmount", 10000L);
        Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getCurrentBalance_ShouldReturnMaxBalance_WhenUserIsNew() {
        Mono<BalanceResponse> result = paymentService.getCurrentBalance("user_1");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(10000L, response.getAmount());
                })
                .verifyComplete();
    }

    @Test
    void chargeIdempotent_ShouldLockAndCharge_WhenRequestIsFirst() {
        String key = "uuid-123";
        String userId = "user_2";
        Long amount = 3000L;
        String redisKey = "idempotency:payment:" + key;

        Mockito.when(valueOperations.setIfAbsent(Mockito.eq(redisKey),
                        Mockito.eq("PROCESSING"), Mockito.any(Duration.class)))
                .thenReturn(Mono.just(true));

        Mockito.when(valueOperations.set(Mockito.eq(redisKey),
                        Mockito.any(PaymentSuccessResponse.class), Mockito.any(Duration.class)))
                .thenReturn(Mono.just(true));

        Mono<PaymentSuccessResponse> result = paymentService.chargeIdempotent(key, userId, amount);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("success", response.getStatus());
                })
                .verifyComplete();

        StepVerifier.create(paymentService.getCurrentBalance(userId))
                .assertNext(balance -> assertEquals(7000L, balance.getAmount()))
                .verifyComplete();
    }

    @Test
    void chargeIdempotent_ShouldDeleteKeyFromRedis_WhenFundsAreInsufficient() {
        String key = "uuid-456";
        String userId = "user_3";
        Long amount = 15000L;
        String redisKey = "idempotency:payment:" + key;

        Mockito.when(valueOperations.setIfAbsent(Mockito.eq(redisKey), Mockito.eq("PROCESSING"), Mockito.any(Duration.class)))
                .thenReturn(Mono.just(true));

        Mockito.when(redisTemplate.delete(redisKey)).thenReturn(Mono.just(1L));

        Mono<PaymentSuccessResponse> result = paymentService.chargeIdempotent(key, userId, amount);

        StepVerifier.create(result)
                .expectError(InsufficientFundsException.class)
                .verify();

        Mockito.verify(redisTemplate, Mockito.times(1)).delete(redisKey);
    }

    @Test
    void chargeIdempotent_ShouldThrowConflictException_WhenRequestIsDuplicateAndProcessing() {
        String key = "uuid-789";
        String userId = "user_4";
        Long amount = 1000L;
        String redisKey = "idempotency:payment:" + key;

        Mockito.when(valueOperations.setIfAbsent(Mockito.eq(redisKey), Mockito.eq("PROCESSING"), Mockito.any(Duration.class)))
                .thenReturn(Mono.just(false));

        Mockito.when(valueOperations.get(redisKey)).thenReturn(Mono.just("PROCESSING"));

        Mono<PaymentSuccessResponse> result = paymentService.chargeIdempotent(key, userId, amount);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof ConflictException
                        && throwable.getMessage().equals("Платеж уже обрабатывается. Пожалуйста, подождите."))
                .verify();
    }

    @Test
    void chargeIdempotent_ShouldReturnCachedResponse_WhenRequestIsDuplicateAndFinished() {
        String key = "uuid-000";
        String userId = "user_5";
        Long amount = 2000L;
        String redisKey = "idempotency:payment:" + key;

        PaymentSuccessResponse cachedResponse = new PaymentSuccessResponse();
        cachedResponse.setStatus("success");

        Mockito.when(valueOperations.setIfAbsent(Mockito.eq(redisKey), Mockito.eq("PROCESSING"), Mockito.any(Duration.class)))
                .thenReturn(Mono.just(false));

        Mockito.when(valueOperations.get(redisKey)).thenReturn(Mono.just(cachedResponse));

        Mono<PaymentSuccessResponse> result = paymentService.chargeIdempotent(key, userId, amount);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("success", response.getStatus());
                })
                .verifyComplete();

        StepVerifier.create(paymentService.getCurrentBalance(userId))
                .assertNext(balance -> assertEquals(10000L, balance.getAmount()))
                .verifyComplete();
    }
}
