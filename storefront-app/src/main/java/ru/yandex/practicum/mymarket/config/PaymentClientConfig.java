package ru.yandex.practicum.mymarket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.ApiClient;
import ru.yandex.practicum.mymarket.api.PaymentApi;

@Configuration
public class PaymentClientConfig {

    @Value("${app.payment-service.url:http://localhost:8081}")
    private String paymentServiceUrl;

    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {

        var authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        var authorizedClientManager = new DefaultReactiveOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientRepository);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    @Bean
    public ApiClient paymentApiClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {

        var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId("payment-service-client");

        ExchangeFilterFunction userIdPropagatingFilter = (request, next) ->
                ReactiveSecurityContextHolder.getContext()
                        .map(SecurityContext::getAuthentication)
                        .map(Authentication::getName)
                        .flatMap(username -> {
                            ClientRequest modifiedRequest = ClientRequest.from(request)
                                    .header("X-User-Id", username)
                                    .build();
                            return next.exchange(modifiedRequest);
                        })
                        .switchIfEmpty(Mono.defer(() -> next.exchange(request)));


        ExchangeFilterFunction idempotencyFilter = (request, next) ->
                Mono.deferContextual(context -> {
                    if (context.hasKey("CUSTOM_IDEMPOTENCY_KEY")) {
                        String key = context.get("CUSTOM_IDEMPOTENCY_KEY");
                        ClientRequest modifiedRequest = ClientRequest.from(request)
                                .header("X-Idempotency-Key", key)
                                .build();
                        return next.exchange(modifiedRequest);
                    }
                    return next.exchange(request);
                });


        WebClient.Builder webClientBuilder = WebClient.builder()
                .filter(oauth2Filter)
                .filter(userIdPropagatingFilter)
                .filter(idempotencyFilter);

        ApiClient apiClient = new ApiClient(webClientBuilder.build());

        return apiClient.setBasePath(paymentServiceUrl);
    }

    @Bean
    public PaymentApi paymentApi(ApiClient apiClient) {
        return new PaymentApi(apiClient);
    }
}
