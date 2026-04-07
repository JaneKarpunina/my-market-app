package ru.yandex.practicum.mymarket.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.exception.ProductNotFoundException;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ItemsService {

    public static final String ALPHA = "ALPHA";
    public static final String PRICE = "PRICE";
    public static final String PLUS = "PLUS";
    public static final String MINUS = "MINUS";

    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public ItemsService(ProductRepository productRepository, CartRepository cartRepository,
                        CartItemRepository cartItemRepository) {
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional
    public ItemsWithPaging getItemsWithPaging(String search, String sort, int pageNumber, int pageSize, String cartId) {

        List<ItemDto> items = getItems(search, sort, cartId);

        int totalItems = items.size();

        if (totalItems == 0) {
            List<List<ItemDto>> itemRows = new ArrayList<>();
            return new ItemsWithPaging(itemRows, new Paging(pageSize, pageNumber, false, false));
        }
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        if (pageNumber < 1) pageNumber = 1;
        if (pageNumber > totalPages) pageNumber = totalPages;

        int fromIndex = (pageNumber - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<ItemDto> pageItems = items.subList(fromIndex, toIndex);

        List<List<ItemDto>> itemRows = new ArrayList<>();
        for (int i = 0; i < pageItems.size(); i += 3) {
            int end = Math.min(i + 3, pageItems.size());
            List<ItemDto> subList = new ArrayList<>(pageItems.subList(i, end));

            while (subList.size() < 3) {

                ItemDto item = new ItemDto();
                item.setId(-1L);
                subList.add(item);
            }

            itemRows.add(subList);
        }

        boolean hasPrevious = pageNumber > 1;
        boolean hasNext = pageNumber < totalPages;

        return new ItemsWithPaging(itemRows, new Paging(pageSize, pageNumber, hasPrevious, hasNext));


    }

    private List<ItemDto> getItems(String search, String sort, String cartId) {

        List<ItemDto> items;
        if (cartId != null) {
            items = productRepository.findProductsWithQuantity(search, cartId);
        }
        else {
            items = productRepository.findProductsWithZeroCartId(search);
        }

        switch (sort) {
            case ALPHA:
                items.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
                break;
            case PRICE:
                items.sort(Comparator.comparingLong(ItemDto::getPrice));
                break;
            default:
                break;
        }
        return items;
    }

    @Transactional
    public void changeItemsCount(Long id, String action, HttpServletResponse response, String cartId) {

        checkItemExists(id);

        if ((cartId == null || cartId.isEmpty())) {

            if (PLUS.equals(action)) {
                cartId = UUID.randomUUID().toString();
                Cookie cookie = new Cookie("cartId", cartId);
                cookie.setMaxAge(7 * 24 * 60 * 60); // Жизнь куки — 7 дней
                cookie.setPath("/");               // Доступна для всего сайта
                cookie.setHttpOnly(true);
                response.addCookie(cookie);

                Cart cart = new Cart();
                cart.setId(cartId);

                cartRepository.save(cart);
                cartItemRepository.insertCartItem(cartId, id, 1); //создается новый элемент
            }
            return;
        }

        setCartItem(id, action, cartId);


    }

    private void setCartItem(Long id, String action, String cartId) {
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cartId, id).orElse(null);

        if (cartItem == null) {
            if (PLUS.equals(action)) {
                cartItemRepository.insertCartItem(cartId, id, 1); //создается новый элемент
            }
            return;
        }

        int quantity = cartItem.getQuantity();
        if (quantity == 1 && MINUS.equals(action)) {
            cartItemRepository.delete(cartItem);
        }
        else if (quantity > 1 && MINUS.equals(action)) {
            cartItemRepository.updateQuantity(cartItem.getId(), quantity - 1);
        }
        else if (quantity < Integer.MAX_VALUE && PLUS.equals(action)) {
            int i = cartItemRepository.updateQuantity(cartItem.getId(), quantity + 1);
        }
    }

    @Transactional
    public ItemDto getItemWithQuantity(Long id, String cartId) {

        checkItemExists(id);

        return getItemDto(id, cartId);
    }

    private ItemDto getItemDto(Long id, String cartId) {
        if (cartId == null || cartId.isEmpty()) {
            return productRepository.findProductWithZeroCartId(id).orElse(new ItemDto());
        }
        else {
            return productRepository.findProductWithQuantity(id, cartId).orElse(new ItemDto());
        }
    }

    private void checkItemExists(Long id) {
        Product product = productRepository.findById(id).orElse(null);

        if (product == null) {
            throw new ProductNotFoundException("Не существует товара с id: " + id);
        }
    }
}
