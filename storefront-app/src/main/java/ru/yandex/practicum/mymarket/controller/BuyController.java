package ru.yandex.practicum.mymarket.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.User;
import ru.yandex.practicum.mymarket.service.OrdersService;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/buy")
public class BuyController {

    private final OrdersService ordersService;

    public BuyController(OrdersService ordersService) {
        this.ordersService = ordersService;
    }


//    @PostMapping
//    public Mono<String> buy(@RequestParam("idempotencyKey") String idempotencyKey,
//                            @AuthenticationPrincipal User currentUser) {
//
//        return ordersService.processOrder(currentUser.getId(), idempotencyKey)
//                .map(id -> "redirect:/orders/" + id + "?newOrder=true")
//                .onErrorResume(e ->
//                    Mono.just("redirect:/cart/items?error=" +
//                            UriUtils.encode(e.getMessage(), StandardCharsets.UTF_8))
//                );
//    }

    @PostMapping
    public Mono<String> buy(
            @AuthenticationPrincipal User currentUser,
            ServerWebExchange exchange) {

        return exchange.getFormData()
                .flatMap(formData -> {
                    String idempotencyKey = formData.getFirst("idempotencyKey");

                    if (idempotencyKey == null || idempotencyKey.isBlank()) {
                        return Mono.error(new RuntimeException("Ключ идемпотентности не найден в форме"));
                    }

                    return ordersService.processOrder(currentUser.getId(), idempotencyKey);
                })
                .map(id -> "redirect:/orders/" + id + "?newOrder=true")
                .onErrorResume(e ->
                        Mono.just("redirect:/cart/items?error=" +
                                UriUtils.encode(e.getMessage(), StandardCharsets.UTF_8))
                );
    }
}
