package ru.yandex.practicum.mymarket.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderFlatRow {

  private Long productId;

  private Long orderId;

  private int quantity;

  private Long price;

  private String title;

}
