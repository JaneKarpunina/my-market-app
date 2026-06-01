package ru.yandex.practicum.mymarket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.mymarket.ApiClient;
import ru.yandex.practicum.mymarket.api.PaymentApi;

@Configuration
public class PaymentClientConfig {

    @Value("${app.payment-service.url:http://localhost:8081}")
    private String paymentServiceUrl;

    @Bean
    public ApiClient paymentApiClient() {
        return new ApiClient().setBasePath(paymentServiceUrl);
    }

    @Bean
    public PaymentApi paymentApi(ApiClient apiClient) {
        return new PaymentApi(apiClient);
    }
}
