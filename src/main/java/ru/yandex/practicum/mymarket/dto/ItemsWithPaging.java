package ru.yandex.practicum.mymarket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ItemsWithPaging {

    private List<List<ItemDto>> items;

    private Paging paging;
}
