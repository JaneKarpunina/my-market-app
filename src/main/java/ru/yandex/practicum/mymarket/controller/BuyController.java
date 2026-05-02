package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.OrdersService;

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

        return ordersService.saveOrder(cartId, response)
                .map(id -> {
                    // 2. Строим URL для редиректа
                    String url = UriComponentsBuilder.fromPath("/orders/{id}")
                            .queryParam("newOrder", true)
                            .buildAndExpand(id)
                            .toUriString();

                    // 3. Возвращаем строку с префиксом redirect:
                    return "redirect:" + url;
                });
    }
}
