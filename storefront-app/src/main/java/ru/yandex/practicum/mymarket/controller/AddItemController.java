package ru.yandex.practicum.mymarket.controller;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.AddItemService;

@Controller
public class AddItemController {

    private final AddItemService addItemService;

    public AddItemController(AddItemService addItemService) {
        this.addItemService = addItemService;
    }

    @GetMapping("/add-item")
    public Mono<Rendering> addProducts(
            @RequestParam(value = "success", required = false, defaultValue = "false") String success) {

        return Mono.just(
                Rendering.view("addItem")
                        .modelAttribute("success", success)
                        .build());
    }

    @PostMapping("/item/add")
    public Mono<String> addProduct(
            @RequestPart String title,
            @RequestPart String description,
            @RequestPart String price,
            @RequestPart("image") FilePart image
    ) {
        return addItemService.addItem(title, description, price, image)
                .thenReturn("redirect:/add-item?success=true");

    }
}
