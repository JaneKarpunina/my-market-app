package ru.yandex.practicum.mymarket.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;


@Data
@Table(name = "ORDERS")
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private Long id;

    @Version
    private Long version;
}
