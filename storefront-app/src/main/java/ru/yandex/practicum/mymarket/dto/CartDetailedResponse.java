package ru.yandex.practicum.mymarket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CartDetailedResponse {

    private CartDto cart;

    private Long balance;

    private boolean canOrder;

    private String errorMessage;
}
