package ru.yandex.practicum.mymarket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table(name = "CART_ITEM")
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    @Id
    private Long id;

    @Column("CART_ID")
    private String cartId;

    @Column("PRODUCT_ID")
    private Long productId;

    private int quantity;

    @Version
    private Long version;
}
