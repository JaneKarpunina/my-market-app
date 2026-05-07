package ru.yandex.practicum.mymarket.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Paging {

    private int pageSize;

    private int pageNumber;

    private boolean hasPrevious;

    private boolean hasNext;
}
