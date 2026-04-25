package ru.yandex.practicum.mymarket.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table(name = "PRODUCT")
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    private Long id;

    private String title;

    private String description;

    @Column("IMG_PATH")
    private String imgPath;

    private long price;

    @Version
    private Long version;
}
