package ru.yandex.practicum.mymarket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemDto {

    private Long id;

    private String title;

    private String description;

    private String imgPath;

    private Long price;

    private int count;
}
