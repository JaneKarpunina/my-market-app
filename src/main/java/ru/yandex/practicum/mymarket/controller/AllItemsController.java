package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.service.ItemsService;

@Controller
public class AllItemsController {

    private final ItemsService itemsService;

    public AllItemsController(ItemsService itemsService) {
        this.itemsService = itemsService;
    }

    @GetMapping(value = {"/", "/items"})
    public String getItems(@RequestParam(value = "search", required = false) String search,
                           @RequestParam(value = "sort", required = false, defaultValue = "NO") String sort,
                           @RequestParam(value = "pageNumber", required = false, defaultValue = "1") int pageNumber,
                           @RequestParam(value = "pageSize", required = false, defaultValue = "5") int pageSize,
                           @CookieValue(value = "cartId", required = false) String cartId,
                           Model model) {

;       ItemsWithPaging itemsWithPaging= itemsService.getItemsWithPaging(search, sort, pageNumber, pageSize, cartId);

        model.addAttribute("items", itemsWithPaging.getItems());
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        model.addAttribute("paging", itemsWithPaging.getPaging());

        return "items";
    }

}
