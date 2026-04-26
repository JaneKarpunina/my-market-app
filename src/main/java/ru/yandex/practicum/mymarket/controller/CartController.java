package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemChangeRequest;
import ru.yandex.practicum.mymarket.service.CartService;

@Controller
@RequestMapping("/cart/items")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Mono<String> getCart(@CookieValue(value = "cartId", required = false) String cartId,
                                Model model) {

        return cartService.getCartDto(cartId)
                        .flatMap(cartDto ->{
                            model.addAttribute("items", cartDto.getItems());
                            model.addAttribute("total", cartDto.getTotal());
                            return Mono.just("cart");
                        });




    }

    @PostMapping
    public Mono<Rendering> changeItemQuantity(
            ItemChangeRequest itemChangeRequest,
            @CookieValue(value = "cartId", required = false) String cartId)
             {

        return cartService.changeItemQuantity(itemChangeRequest.getId(),
                itemChangeRequest.getAction(), cartId)
                .then(Mono.defer(() -> cartService.getCartDto(cartId)))
                .map(cartDto -> Rendering.view("cart")
                        .modelAttribute("items",  cartDto.getItems())
                        .modelAttribute("total", cartDto.getTotal())
                        .build());
    }
}
