package ru.yandex.practicum.mymarket.service;

import org.springframework.boot.test.mock.mockito.MockBean;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;

public class BaseTest {

    @MockBean
    public CartRepository cartRepository;

    @MockBean
    public CartItemRepository cartItemRepository;
}
