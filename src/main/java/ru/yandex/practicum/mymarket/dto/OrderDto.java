package ru.yandex.practicum.mymarket.dto;

import java.util.List;

public class OrderDto {

    Long id;

    List<ItemDto> items;

    long totalSum;
}
