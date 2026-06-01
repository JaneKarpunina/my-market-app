package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.OrdersService;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/buy")
public class BuyController {

    private final OrdersService ordersService;

    public BuyController(OrdersService ordersService) {
        this.ordersService = ordersService;
    }


    @PostMapping
    public Mono<String> buy(@CookieValue(value = "cartId", required = false) String cartId,
                            ServerHttpResponse response) {

        return ordersService.processOrder(cartId, response)
                .map(id -> "redirect:/orders/" + id + "?newOrder=true")
                .onErrorResume(e ->
                    Mono.just("redirect:/cart/items?error=" +
                            UriUtils.encode(e.getMessage(), StandardCharsets.UTF_8))
                );
    }
}
