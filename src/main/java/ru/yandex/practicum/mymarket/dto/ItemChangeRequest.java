package ru.yandex.practicum.mymarket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemChangeRequest {

    @NotNull
    private Long id;

    private String search = "";

    private String sort = "NO";

    private int pageNumber = 1;

    private int pageSize = 5;

    @NotNull
    private String action;
}
