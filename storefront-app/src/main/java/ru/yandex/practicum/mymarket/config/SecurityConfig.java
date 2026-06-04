package ru.yandex.practicum.mymarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import java.net.URI;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/items", "/items/*").authenticated()
                        .pathMatchers("/add-item", "/item/add", "/cart/items", "/orders",
                                "/orders/*", "/buy")
                        .authenticated() // Корзина и заказы только для авторизованных
                        .anyExchange().permitAll() // Витрина, товары и статика доступны всем
                )
                .formLogin(form -> form
                                // Настраиваем редирект после УСПЕШНОГО входа (например, на главную страницу /)
                                .authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler("/"))
                        // Если у вас кастомная HTML-страница логина (например, Thymeleaf), раскомментируйте строку ниже:
                        // .loginPage("/login")
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        // Настраиваем редирект после ЛОГАУТА (тоже возвращаем на витрину)
                        .logoutSuccessHandler(logoutSuccessHandler("/"))
                )
                //.csrf(ServerHttpSecurity.CsrfSpec::disable) // Отключаем для простоты работы с POST-формами
                .build();
    }

    // Хелпер-метод для настройки редиректа после выхода из аккаунта
    private RedirectServerLogoutSuccessHandler logoutSuccessHandler(String uri) {
        RedirectServerLogoutSuccessHandler logoutSuccessHandler = new RedirectServerLogoutSuccessHandler();
        logoutSuccessHandler.setLogoutSuccessUrl(URI.create(uri));
        return logoutSuccessHandler;
    }
}

