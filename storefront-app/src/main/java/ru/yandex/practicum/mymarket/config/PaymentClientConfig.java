package ru.yandex.practicum.mymarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.mymarket.ApiClient;
import ru.yandex.practicum.mymarket.api.PaymentApi;

@Configuration
public class PaymentClientConfig {
    @Bean
    public ApiClient paymentApiClient() {
        return new ApiClient().setBasePath("http://localhost:8081");
    }

    @Bean
    public PaymentApi paymentApi(ApiClient apiClient) {
        return new PaymentApi(apiClient);
    }
}
