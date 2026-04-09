package ru.yandex.practicum.mymarket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {

    Long id;

    List<ItemDto> items;

    long totalSum;
}
