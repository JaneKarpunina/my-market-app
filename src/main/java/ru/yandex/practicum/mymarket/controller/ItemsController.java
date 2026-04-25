package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemChangeRequest;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.ItemsService;

@Controller
@RequestMapping("/items")
public class ItemsController {

    private final ItemsService itemsService;

    public ItemsController(ItemsService itemsService) {
        this.itemsService = itemsService;
    }

//    @PostMapping
//    public String changeItemQuantity(
//            @RequestParam("id") Long id,
//            @RequestParam(value = "search", required = false, defaultValue = "") String search,
//            @RequestParam(value = "sort", required = false, defaultValue = "NO") String sort,
//            @RequestParam(value = "pageNumber", required = false, defaultValue = "1") int pageNumber,
//            @RequestParam(value = "pageSize", required = false, defaultValue = "5") int pageSize,
//            @RequestParam("action") String action,
//            HttpServletResponse response,
//            @CookieValue(value = "cartId", required = false) String cartId) {
//
//        itemsService.changeItemsCount(id, action, response, cartId);
//
//        String url = UriComponentsBuilder.fromPath("/items")
//                .queryParam("search", search)
//                .queryParam("sort", sort)
//                .queryParam("pageNumber", pageNumber)
//                .queryParam("pageSize", pageSize)
//                .toUriString();
//
//        return "redirect:" + url;
//    }

    @PostMapping
    public Mono<String> changeItemQuantity(
            ItemChangeRequest request,
            @CookieValue(value = "cartId", required = false) String cartId,
            ServerHttpResponse response) {

        return itemsService.changeItemsCount(request.getId(), request.getAction(), response, cartId)
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

    @GetMapping("/{id}")
    public Mono<String> getItem(@PathVariable Long id,
                          @CookieValue(value = "cartId", required = false) String cartId,
                          Model model) {

        return itemsService.getItemWithQuantity(id, cartId)
                        .flatMap(item -> {
                            model.addAttribute("item", item);
                            return Mono.just("item");
                        });

    }

    @PostMapping("/{id}")
    public Mono<Rendering> changeItemQuantity(@PathVariable Long id,
                                              @CookieValue(value = "cartId", required = false) String cartId,
                                              ItemChangeRequest request,
                                              ServerHttpResponse response) {

        return itemsService.changeItemsCount(id, request.getAction(), response, cartId)
                .then(Mono.defer(() ->
                    itemsService.getItemWithQuantity(id, cartId)
                ))
                .map(item -> Rendering.view("item")
                        .modelAttribute("item", item)
                        .build());
    }
}




