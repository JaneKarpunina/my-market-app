package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.yandex.practicum.mymarket.service.AddItemService;

@Controller
public class AddItemController {

    private final AddItemService addItemService;

    public AddItemController(AddItemService addItemService) {
        this.addItemService = addItemService;
    }

    @GetMapping("/add-item")
    public String addProducts() {
        return "addItem";
    }

    @PostMapping("/item/add")
    public String addProduct(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String price,
            @RequestParam("image") MultipartFile imageFile,
            RedirectAttributes redirectAttributes
    ) {
        addItemService.addItem(title, description, price, imageFile);

        redirectAttributes.addFlashAttribute("successMessage", "Товар успешно добавлен!");
        return "redirect:/add-item";
    }
}
