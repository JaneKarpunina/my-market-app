package ru.yandex.practicum.mymarket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemChangeRequest {

    private Long id;

    private String search;

    private String sort;

    private int pageNumber;

    private int pageSize;

    private String action;
}
