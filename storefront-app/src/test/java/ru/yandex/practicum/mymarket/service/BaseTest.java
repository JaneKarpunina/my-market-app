package ru.yandex.practicum.mymarket.service;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;

public class BaseTest {

    @MockitoBean
    public CartRepository cartRepository;

    @MockitoBean
    public CartItemRepository cartItemRepository;
}
