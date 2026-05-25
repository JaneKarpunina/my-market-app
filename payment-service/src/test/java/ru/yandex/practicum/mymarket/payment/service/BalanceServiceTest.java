package ru.yandex.practicum.mymarket.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.payment.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PaymentService.class)
public class BalanceServiceTest {

    @Autowired
    private PaymentService balanceService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(balanceService, "maxBalanceAmount", 10000L);
    }

    @Test
    void getCurrentBalance_ShouldReturnAmountWithinBounds() {
        Mono<BalanceResponse> balanceMono = balanceService.getCurrentBalance();

        StepVerifier.create(balanceMono)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertNotNull(response.getAmount());
                    assertEquals(10000, (long) response.getAmount(),
                            "Баланс должен быть 10000");
                })
                .verifyComplete();
    }

    @Test
    void charge_ShouldSucceed_WhenBalanceIsZeroAndAmountIsZero() {
        ReflectionTestUtils.setField(balanceService, "maxBalanceAmount", 0L);

        Mono<Void> chargeMono = balanceService.charge(0L);

        StepVerifier.create(chargeMono)
                .verifyComplete();
    }

    @Test
    void charge_ShouldFailWithInsufficientFundsException_WhenAmountIsGreaterThanMaxPossibleBalance() {
        Mono<Void> chargeMono = balanceService.charge(10001L);

        StepVerifier.create(chargeMono)
                .expectErrorMatches(throwable -> throwable instanceof InsufficientFundsException
                        && "Недостаточно средств".equals(throwable.getMessage()))
                .verify();
    }
}
