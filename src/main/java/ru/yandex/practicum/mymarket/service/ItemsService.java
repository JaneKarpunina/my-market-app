package ru.yandex.practicum.mymarket.service;

import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.ItemsWithPaging;
import ru.yandex.practicum.mymarket.dto.Paging;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.CartRepository;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ItemsService {

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

    @Transactional(readOnly = true)
    public Mono<ItemsWithPaging> getItemsWithPaging(String search, String sort, int pageNumber, int pageSize,
                                                    String cartId) {
        String searchPattern = (search == null) ? "" : search;
        long offset = (long) (Math.max(1, pageNumber) - 1) * pageSize;

        return Mono.zip(
                fetchPageItems(searchPattern, sort, pageSize, offset, cartId).collectList(),

                productRepository.countByTitleAndDescription(searchPattern)
        ).map(tuple -> {
            List<ItemDto> pageItems = tuple.getT1();
            long totalItems = tuple.getT2();

            int totalPages = (int) Math.ceil((double) totalItems / pageSize);
            int currentPage = Math.max(1, Math.min(pageNumber, totalPages));

            // Разбиваем на строки по 3 для верстки
            List<List<ItemDto>> itemRows = partitionAndFill(pageItems, 3);

            boolean hasPrevious = currentPage > 1;
            boolean hasNext = currentPage < totalPages;

            return new ItemsWithPaging(itemRows, new Paging(pageSize, currentPage, hasPrevious, hasNext));
        });
    }

    private Flux<ItemDto> fetchPageItems(String search, String sort, int limit, long offset, String cartId) {
        if (cartId != null) {
            return productRepository.findProductsWithQuantityPaged(search, cartId, sort, limit, offset);
        }
        return productRepository.findProductsWithZeroCartIdPaged(search, sort, limit, offset);
    }

    private List<List<ItemDto>> partitionAndFill(List<ItemDto> list, int size) {
        List<List<ItemDto>> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            int end = Math.min(i + size, list.size());
            List<ItemDto> subList = new ArrayList<>(list.subList(i, end));

            // Дополняем "пустыми" объектами для верстки
            while (subList.size() < size) {
                ItemDto placeholder = new ItemDto();
                placeholder.setId(-1L);
                subList.add(placeholder);
            }
            rows.add(subList);
        }
        return rows;
    }

    @Transactional
    public Mono<Void> changeItemsCount(Long id, String action, ServerHttpResponse response, String cartId) {
        return productRepository.findById(id)
                .flatMap(product -> {
                    // Если cartId нет и действие PLUS — создаем новую корзину
                    if (cartId == null || cartId.isEmpty()) {
                        if (PLUS.equals(action)) {
                            String newCartId = UUID.randomUUID().toString();
                            addCartCookie(response, newCartId);
                            return createCartAndItem(id, newCartId);
                        }
                        return Mono.empty();
                    }

                    return cartRepository.findById(cartId)
                            // Если кука есть, но корзины в БД нет (например, удалена)
                            .switchIfEmpty(Mono.defer(() -> {
                                if (PLUS.equals(action)) {
                                    return createCartAndItem(id, cartId).then(Mono.empty());
                                }
                                return Mono.empty();
                            }))
                            .flatMap(cart -> setCartItem(id, action, cartId));


                });
    }

    private void addCartCookie(ServerHttpResponse response, String cartId) {
        ResponseCookie cookie = ResponseCookie.from("cartId", cartId)
                .maxAge(Duration.ofDays(7))
                .path("/")
                .httpOnly(true)
                .build();
        response.addCookie(cookie);
    }

    private Mono<Void> createCartAndItem(Long id, String cartId) {
        Cart cart = new Cart();
        cart.setId(cartId);

        return cartRepository.save(cart)
                .then(Mono.defer(() -> {
                    CartItem item = new CartItem();
                    item.setCartId(cartId);
                    item.setProductId(id);
                    item.setQuantity(1);
                    // version и id не трогаем, они останутся null
                    return cartItemRepository.save(item); }))
                .then();
    }

    private Mono<Void> setCartItem(Long id, String action, String cartId) {
        return cartItemRepository.findByCartIdAndProductId(cartId, id)
                .switchIfEmpty(Mono.defer(() -> {
                    if (PLUS.equals(action)) {
                        return cartItemRepository.save(new CartItem(null, cartId, id, 1, null))
                                .then(Mono.empty());
                    }
                    return Mono.empty();
                }))
                .flatMap(cartItem -> {
                    int quantity = cartItem.getQuantity();

                    if (quantity == 1 && MINUS.equals(action)) {
                        return cartItemRepository.delete(cartItem);
                    }

                    CartItem updatedItem = null;
                    if (quantity > 1 && MINUS.equals(action)) {
                        cartItem.setQuantity(quantity - 1);
                        updatedItem = cartItem;
                    } else if (quantity < Integer.MAX_VALUE && PLUS.equals(action)) {
                        cartItem.setQuantity(quantity + 1);
                        updatedItem = cartItem;
                    }

                    return updatedItem != null ? cartItemRepository.save(updatedItem) : Mono.empty();
                })
                .then();
    }

    @Transactional(readOnly = true)
    public Mono<ItemDto> getItemWithQuantity(Long id, String cartId) {
        return productRepository.findById(id) // Возвращает Mono<Product>
                .flatMap(product -> getItemDto(id, cartId)) // Если продукт найден, идем за DTO
                .defaultIfEmpty(new ItemDto()); // Если продукт не найден в принципе
    }

    private Mono<ItemDto> getItemDto(Long id, String cartId) {
        if (cartId == null || cartId.isEmpty()) {
            return productRepository.findProductWithZeroCartId(id)
                    .defaultIfEmpty(new ItemDto());
        } else {
            return productRepository.findProductWithQuantity(id, cartId)
                    .defaultIfEmpty(new ItemDto());
        }
    }
}
