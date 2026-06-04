package ru.yandex.practicum.mymarket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table(name = "CART")
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    @Id
    private Long id;

    @Column("USER_ID")
    private Long userId;

    @Version
    private Long version;
}
