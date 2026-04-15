package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriComponentsBuilder;
import ru.yandex.practicum.mymarket.service.OrdersService;

@Controller
@RequestMapping("/buy")
public class BuyController {

    private final OrdersService ordersService;

    public BuyController(OrdersService ordersService) {
        this.ordersService = ordersService;
    }


    @PostMapping
    public String buy(@CookieValue(value = "cartId", required = false) String cartId,
                      HttpServletResponse response) {

        Long id = ordersService.saveOrder(cartId, response);

        String url = UriComponentsBuilder.fromPath("/orders/{id}")
                .queryParam("newOrder", true)
                .buildAndExpand(id)
                .toUriString();

        return "redirect:" + url;
    }
}
