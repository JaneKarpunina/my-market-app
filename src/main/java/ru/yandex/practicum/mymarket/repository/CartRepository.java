package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.mymarket.entity.Cart;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {
}
