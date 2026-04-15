package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.service.CartService;

@Controller
@RequestMapping("/cart/items")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public String getCart(@CookieValue(value = "cartId", required = false) String cartId,
                          Model model) {

        CartDto cartDto = cartService.getCartDto(cartId);

        model.addAttribute("items", cartDto.getItems());
        model.addAttribute("total", cartDto.getTotal());
        return "cart";

    }

    @PostMapping
    public String changeItemQuantity(
            @RequestParam("id") Long id,
            @RequestParam("action") String action,
            @CookieValue(value = "cartId", required = false) String cartId,
            Model model) {


        cartService.changeItemQuantity(id, action, cartId);
        CartDto cartDto = cartService.getCartDto(cartId);

        model.addAttribute("items", cartDto.getItems());
        model.addAttribute("total", cartDto.getTotal());
        return "cart";
    }
}
