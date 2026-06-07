package ru.yandex.practicum.mymarket.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemChangeRequest;
import ru.yandex.practicum.mymarket.entity.User;
import ru.yandex.practicum.mymarket.service.CartService;

@Controller
@RequestMapping("/cart/items")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Mono<String> getCart(@AuthenticationPrincipal User currentUser,
                                @RequestParam(value = "error", required = false) String error,
                                Model model) {

        return cartService.getCartDetailed(currentUser.getId())
                .doOnNext(data -> {
                    model.addAttribute("items", data.getCart().getItems());
                    model.addAttribute("total", data.getCart().getTotal());
                    model.addAttribute("canOrder", data.isCanOrder());
                    model.addAttribute("errorMessage", data.getErrorMessage());
                    model.addAttribute("error", error);
                })
                .thenReturn("cart");
    }

    @PostMapping
    public Mono<Rendering> changeItemQuantity(
            ItemChangeRequest itemChangeRequest,
            @AuthenticationPrincipal User currentUser) { // Достаем текущего пользователя

        Long userId = currentUser.getId();

        return cartService.changeItemQuantity(itemChangeRequest.getId(), itemChangeRequest.getAction(), userId)
                .then(Mono.defer(() -> cartService.getCartDetailed(userId))) // Получаем детали корзины по userId
                .map(data -> Rendering.view("cart")
                        .modelAttribute("items",  data.getCart().getItems())
                        .modelAttribute("total", data.getCart().getTotal())
                        .modelAttribute("canOrder",  data.isCanOrder())
                        .modelAttribute("errorMessage", data.getErrorMessage())
                        .build());
    }


}
