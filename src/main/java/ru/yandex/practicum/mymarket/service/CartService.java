package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;

import java.util.List;

@Service
public class CartService {

    public static final String PLUS = "PLUS";
    public static final String MINUS = "MINUS";
    public static final String DELETE = "DELETE";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional
    public CartDto getCartDto(String cartId) {

        if (cartId == null || cartId.isEmpty()) {
           return new CartDto(List.of(), 0L);
        }

        Cart savedCart = cartRepository.findById(cartId).orElse(null);

        if (savedCart == null) {
            Cart cart = new Cart();
            cart.setId(cartId);
            cartRepository.save(cart);
            return new CartDto(List.of(), 0L);
        }

        List<ItemDto> itemDtos = cartRepository.findItemsForCartId(cartId);

        long total = 0L;
        for (ItemDto itemDto : itemDtos) {
           total += itemDto.getPrice() * itemDto.getCount();
        }
        return new CartDto(itemDtos, total);
    }

    @Transactional
    public void changeItemQuantity(Long id, String action, String cartId) {

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cartId, id).orElse(null);

        if (cartItem == null) {
            return;
        }

        int quantity = cartItem.getQuantity();
        if (quantity == 1 && MINUS.equals(action) || DELETE.equals(action)) {
            cartItemRepository.delete(cartItem);
        }
        else if (quantity > 1 && MINUS.equals(action)) {
            cartItemRepository.updateQuantity(cartItem.getId(), quantity - 1);
        }
        else if (quantity < Integer.MAX_VALUE && PLUS.equals(action)) {
            cartItemRepository.updateQuantity(cartItem.getId(), quantity + 1);
        }

    }
}
