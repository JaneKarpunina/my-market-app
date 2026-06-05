package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemChangeRequest;
import ru.yandex.practicum.mymarket.entity.User;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemsService;

@Controller
@RequestMapping("/items")
public class ItemsController {

    private final ItemsService itemsService;

    private final CartService cartService;

    public ItemsController(ItemsService itemsService, CartService cartService) {
        this.itemsService = itemsService;
        this.cartService = cartService;
    }

    @PostMapping
    public Mono<String> changeItemQuantity(
            ItemChangeRequest request,
            @AuthenticationPrincipal User currentUser) { // Достаем текущего пользователя

        return cartService.changeItemsCount(request.getId(), request.getAction(), currentUser.getId())
                .then(Mono.fromCallable(() ->
                        UriComponentsBuilder.fromPath("/items")
                                .queryParam("search", request.getSearch())
                                .queryParam("sort", request.getSort())
                                .queryParam("pageNumber", request.getPageNumber())
                                .queryParam("pageSize", request.getPageSize())
                                .toUriString()
                ))
                .map(url -> "redirect:" + url);
    }


//    @PostMapping
//    public Mono<String> changeItemQuantity(
//            ItemChangeRequest request,
//            @CookieValue(value = "cartId", required = false) String cartId,
//            ServerHttpResponse response) {
//
//        return cartService.changeItemsCount(request.getId(), request.getAction(), response, cartId)
//                .then(Mono.fromCallable(() ->
//                        UriComponentsBuilder.fromPath("/items")
//                                .queryParam("search", request.getSearch())
//                                .queryParam("sort", request.getSort())
//                                .queryParam("pageNumber", request.getPageNumber())
//                                .queryParam("pageSize", request.getPageSize())
//                                .toUriString()
//                ))
//                .map(url -> "redirect:" + url);
//    }

    @GetMapping("/{id}")
    public Mono<String> getItem(@PathVariable Long id,
                                @AuthenticationPrincipal User currentUser,
                          Model model) {

        Long userId = (currentUser != null) ? currentUser.getId() : null;

        return itemsService.getItemWithQuantity(id, userId)
                        .flatMap(item -> {
                            model.addAttribute("item", item);
                            return Mono.just("item");
                        });

    }

    @PostMapping("/{id}")
    public Mono<Rendering> changeItemQuantity(@PathVariable Long id,
                                              ItemChangeRequest request,
                                              @AuthenticationPrincipal User currentUser) {

        return cartService.changeItemsCount(id, request.getAction(), currentUser.getId())
                .then(Mono.defer(() ->
                    itemsService.getItemWithQuantity(id, currentUser.getId())
                ))
                .map(item -> Rendering.view("item")
                        .modelAttribute("item", item)
                        .build());
    }
}




