package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.ItemsService;

@Controller
@RequestMapping("/items")
public class ItemsController {

    private final ItemsService itemsService;

    public ItemsController(ItemsService itemsService) {
        this.itemsService = itemsService;
    }

    @PostMapping
    public String changeItemQuantity(
            @RequestParam("id") Long id,
            @RequestParam(value = "search", required = false, defaultValue = "") String search,
            @RequestParam(value = "sort", required = false, defaultValue = "NO") String sort,
            @RequestParam(value = "pageNumber", required = false, defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", required = false, defaultValue = "5") int pageSize,
            @RequestParam("action") String action,
            HttpServletResponse response,
            @CookieValue(value = "cartId", required = false) String cartId) {

        itemsService.changeItemsCount(id, action, response, cartId);

        String url = UriComponentsBuilder.fromPath("/items")
                .queryParam("search", search)
                .queryParam("sort", sort)
                .queryParam("pageNumber", pageNumber)
                .queryParam("pageSize", pageSize)
                .toUriString();

        return "redirect:" + url;
    }

    @GetMapping("/{id}")
    public String getItem(@PathVariable Long id,
                          @CookieValue(value = "cartId", required = false) String cartId,
                          Model model) {

        ItemDto item  = itemsService.getItemWithQuantity(id, cartId);

        model.addAttribute("item", item);
        return "item";
    }

    @PostMapping("/{id}")
    public String changeItemQuantity(@PathVariable Long id,
                                     @CookieValue(value = "cartId", required = false) String cartId,
                                     @RequestParam("action") String action,
                                     HttpServletResponse response,
                                     Model model) {

        itemsService.changeItemsCount(id, action, response, cartId);
        ItemDto item  = itemsService.getItemWithQuantity(id, cartId);

        model.addAttribute("item", item);

        return "item";
    }
}




