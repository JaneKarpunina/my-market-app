package ru.yandex.practicum.mymarket.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table(name = "ORDER_ITEM")
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    private Long id;

    @Column("ORDER_ID")
    private Long orderId;

    @Column("PRODUCT_ID")
    private Long productId;

    private int quantity;

    @Version
    private Long version;
}
