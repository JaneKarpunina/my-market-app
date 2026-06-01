package ru.yandex.practicum.mymarket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CartDto {

    private List<ItemDto> items;

    private long total;
}
