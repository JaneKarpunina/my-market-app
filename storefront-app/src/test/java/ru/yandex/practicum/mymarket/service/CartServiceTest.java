package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.api.PaymentApi;
import ru.yandex.practicum.mymarket.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.dto.CartDetailedResponse;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;

@SpringBootTest(classes = CartService.class)
public class CartServiceTest extends BaseTest {

    @Autowired
    @SpyBean
    private CartService cartService;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private PaymentApi paymentApi;

    @MockBean
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ReactiveValueOperations<String, Object> valueOperations;



    @BeforeEach
    void resetMocks() {
        reset(cartRepository, cartItemRepository, productRepository, paymentApi,
                redisTemplate, valueOperations);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getCartDto_ShouldReturnEmptyCart_WhenUserIdIsNull() {
        Mono<CartDto> result = cartService.getCartDto(null);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertNotNull(cartDto);
                    assertEquals(0L, cartDto.getTotal());
                    assertEquals(0, cartDto.getItems().size());
                })
                .verifyComplete();
    }

    @Test
    void getCartDto_ShouldReturnEmptyCart_WhenCartHasNoItems() {
        Long userId = 1L;
        Long cartId = 100L;

        Cart mockCart = new Cart();
        mockCart.setId(cartId);
        mockCart.setUserId(userId);

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.empty());

        Mono<CartDto> result = cartService.getCartDto(userId);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertEquals(0L, cartDto.getTotal());
                    assertEquals(0, cartDto.getItems().size());
                })
                .verifyComplete();
    }

    @Test
    void getCartDto_ShouldReturnProductsFromRedisCache_WhenCacheExists() {
        Long userId = 1L;
        Long cartId = 100L;
        Long productId = 500L;

        Cart mockCart = new Cart();
        mockCart.setId(cartId);
        mockCart.setUserId(userId);

        CartItem mockItem = new CartItem();
        mockItem.setCartId(cartId);
        mockItem.setProductId(productId);
        mockItem.setQuantity(3);

        Product cachedProduct = new Product();
        cachedProduct.setId(productId);
        cachedProduct.setTitle("Телефон из кэша");
        cachedProduct.setPrice(2000L);

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(mockItem));

        List<String> expectedCacheKeys = List.of("product:" + productId);
        Mockito.when(valueOperations.multiGet(expectedCacheKeys))
                .thenReturn(Mono.just(List.of(cachedProduct))); // Нашли продукт в Redis!

        Mono<CartDto> result = cartService.getCartDto(userId);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertEquals(6000L, cartDto.getTotal()); // 2000 * 3
                    assertEquals(1, cartDto.getItems().size());
                    assertEquals("Телефон из кэша", cartDto.getItems().getFirst().getTitle());
                })
                .verifyComplete();

        Mockito.verify(productRepository, Mockito.never()).findAllById(Mockito.anyCollection());
    }

    @Test
    void getCartDto_ShouldFetchFromDbAndCache_WhenRedisCacheIsEmpty() {
        Long userId = 1L;
        Long cartId = 100L;
        Long productId = 700L;

        Cart mockCart = new Cart();
        mockCart.setId(cartId);
        mockCart.setUserId(userId);

        CartItem mockItem = new CartItem();
        mockItem.setCartId(cartId);
        mockItem.setProductId(productId);
        mockItem.setQuantity(2);

        Product dbProduct = new Product();
        dbProduct.setId(productId);
        dbProduct.setTitle("Телевизор из БД");
        dbProduct.setPrice(15000L);

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(mockItem));

        List<String> expectedCacheKeys = List.of("product:" + productId);
        List<Object> emptyRedisResult = new ArrayList<>();
        emptyRedisResult.add(null);
        Mockito.when(valueOperations.multiGet(expectedCacheKeys)).thenReturn(Mono.just(emptyRedisResult));

        Mockito.when(productRepository.findAllById(List.of(productId))).thenReturn(Flux.just(dbProduct));

        Mockito.when(valueOperations.multiSet(Mockito.anyMap())).thenReturn(Mono.just(true));
        Mockito.when(redisTemplate.expire(Mockito.anyString(), Mockito.any())).thenReturn(Mono.just(true));

        Mono<CartDto> result = cartService.getCartDto(userId);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertEquals(30000L, cartDto.getTotal());
                    assertEquals(1, cartDto.getItems().size());
                    assertEquals("Телевизор из БД", cartDto.getItems().getFirst().getTitle());
                })
                .verifyComplete();

        Mockito.verify(productRepository, Mockito.times(1)).findAllById(List.of(productId));
        Mockito.verify(valueOperations, Mockito.times(1)).multiSet(Mockito.anyMap());
    }

    @Test
    void getCartDto_ShouldCreateNewCart_WhenCartDoesNotExistInDb() {
        Long userId = 55L;
        Long newCartId = 888L;

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.empty());

        Mockito.when(cartRepository.save(Mockito.any(Cart.class))).thenAnswer(invocation -> {
            Cart cartToSave = invocation.getArgument(0);
            cartToSave.setId(newCartId);
            return Mono.just(cartToSave);
        });

        Mockito.when(cartItemRepository.findByCartId(newCartId)).thenReturn(Flux.empty());

        Mono<CartDto> result = cartService.getCartDto(userId);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertEquals(0L, cartDto.getTotal());
                    assertEquals(0, cartDto.getItems().size());
                })
                .verifyComplete();

        Mockito.verify(cartRepository, Mockito.times(1)).save(Mockito.any(Cart.class));
    }

    @Test
    void changeItemQuantity_ShouldIncreaseQuantity_WhenActionIsPlus() {
        Long productId = 10L;
        Long userId = 1L;
        Long cartId = 100L;

        Cart mockCart = new Cart();
        mockCart.setId(cartId);

        CartItem mockCartItem = new CartItem();
        mockCartItem.setCartId(cartId);
        mockCartItem.setProductId(productId);
        mockCartItem.setQuantity(5); // Было 5 штук

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartIdAndProductId(cartId, productId))
                .thenReturn(Mono.just(mockCartItem));

        Mockito.when(cartItemRepository.save(Mockito.any(CartItem.class))).thenReturn(Mono.just(mockCartItem));

        Mono<Void> result = cartService.changeItemQuantity(productId, "PLUS", userId);

        StepVerifier.create(result)
                .verifyComplete();

        assertEquals(6, mockCartItem.getQuantity());
        Mockito.verify(cartItemRepository, Mockito.times(1)).save(mockCartItem);
        Mockito.verify(cartItemRepository, Mockito.never()).delete(Mockito.any(CartItem.class));
    }

    @Test
    void changeItemQuantity_ShouldDecreaseQuantity_WhenActionIsMinusAndQuantityGreaterThanOne() {
        Long productId = 10L;
        Long userId = 1L;
        Long cartId = 100L;

        Cart mockCart = new Cart();
        mockCart.setId(cartId);

        CartItem mockCartItem = new CartItem();
        mockCartItem.setCartId(cartId);
        mockCartItem.setProductId(productId);
        mockCartItem.setQuantity(3);

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartIdAndProductId(cartId, productId))
                .thenReturn(Mono.just(mockCartItem));

        Mockito.when(cartItemRepository.save(Mockito.any(CartItem.class))).thenReturn(Mono.just(mockCartItem));

        Mono<Void> result = cartService.changeItemQuantity(productId, "MINUS", userId);

        StepVerifier.create(result).verifyComplete();

        assertEquals(2, mockCartItem.getQuantity());
        Mockito.verify(cartItemRepository, Mockito.times(1)).save(mockCartItem);
        Mockito.verify(cartItemRepository, Mockito.never()).delete(Mockito.any(CartItem.class));
    }


    @Test
    void changeItemQuantity_ShouldDeleteProduct_WhenActionIsMinusAndQuantityIsOne() {
        Long productId = 10L;
        Long userId = 1L;
        Long cartId = 100L;

        Cart mockCart = new Cart();
        mockCart.setId(cartId);

        CartItem mockCartItem = new CartItem();
        mockCartItem.setCartId(cartId);
        mockCartItem.setProductId(productId);
        mockCartItem.setQuantity(1);

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartIdAndProductId(cartId, productId))
                .thenReturn(Mono.just(mockCartItem));

        Mockito.when(cartItemRepository.delete(Mockito.any(CartItem.class))).thenReturn(Mono.empty());

        Mono<Void> result = cartService.changeItemQuantity(productId, "MINUS", userId);

        StepVerifier.create(result).verifyComplete();

        Mockito.verify(cartItemRepository, Mockito.times(1)).delete(mockCartItem);
        Mockito.verify(cartItemRepository, Mockito.never()).save(Mockito.any(CartItem.class));
    }

    @Test
    void changeItemQuantity_ShouldDeleteProductImmediately_WhenActionIsDelete() {
        Long productId = 10L;
        Long userId = 1L;
        Long cartId = 100L;

        Cart mockCart = new Cart();
        mockCart.setId(cartId);

        CartItem mockCartItem = new CartItem();
        mockCartItem.setCartId(cartId);
        mockCartItem.setProductId(productId);
        mockCartItem.setQuantity(15);

        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartIdAndProductId(cartId, productId))
                .thenReturn(Mono.just(mockCartItem));

        Mockito.when(cartItemRepository.delete(Mockito.any(CartItem.class))).thenReturn(Mono.empty());

        Mono<Void> result = cartService.changeItemQuantity(productId, "DELETE", userId);

        StepVerifier.create(result).verifyComplete();

        Mockito.verify(cartItemRepository, Mockito.times(1)).delete(mockCartItem);
        Mockito.verify(cartItemRepository, Mockito.never()).save(Mockito.any(CartItem.class));
    }

    @Test
    void getCartDetailed_ShouldReturnCanOrderTrue_WhenBalanceIsSufficient() {
        Long userId = 1L;

        CartDto mockCart = new CartDto(new java.util.ArrayList<>(), 2000L);
        Mockito.doReturn(Mono.just(mockCart)).when(cartService).getCartDto(userId);

        BalanceResponse mockBalance = new BalanceResponse();
        mockBalance.setAmount(5000L);
        Mockito.when(paymentApi.getBalance()).thenReturn(Mono.just(mockBalance));

        Mono<CartDetailedResponse> result = cartService.getCartDetailed(userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(5000L, response.getBalance());
                    assertEquals(2000L, response.getCart().getTotal());
                    org.junit.jupiter.api.Assertions.assertTrue(response.isCanOrder());
                    org.junit.jupiter.api.Assertions.assertNull(response.getErrorMessage());
                })
                .verifyComplete();

    }

    @Test
    void getCartDetailed_ShouldReturnError_WhenBalanceIsLessThanCartTotal() {
        Long userId = 1L;

        CartDto mockCart = new CartDto(new java.util.ArrayList<>(), 4000L);
        Mockito.doReturn(Mono.just(mockCart)).when(cartService).getCartDto(userId);

        BalanceResponse mockBalance = new BalanceResponse();
        mockBalance.setAmount(1500L);
        Mockito.when(paymentApi.getBalance()).thenReturn(Mono.just(mockBalance));

        Mono<CartDetailedResponse> result = cartService.getCartDetailed(userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(1500L, response.getBalance());
                    org.junit.jupiter.api.Assertions.assertFalse(response.isCanOrder());
                    assertEquals("Недостаточно средств", response.getErrorMessage());
                })
                .verifyComplete();
    }

    @Test
    void getCartDetailed_ShouldReturnUnavailableError_WhenPaymentApiThrowsException() {
        Long userId = 1L;

        CartDto mockCart = new CartDto(new java.util.ArrayList<>(), 1000L);
        Mockito.doReturn(Mono.just(mockCart)).when(cartService).getCartDto(userId);

        Mockito.when(paymentApi.getBalance())
                .thenReturn(Mono.error(new RuntimeException("Сбой сети или таймаут")));

        Mono<CartDetailedResponse> result = cartService.getCartDetailed(userId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(-1L, response.getBalance());
                    org.junit.jupiter.api.Assertions.assertFalse(response.isCanOrder());
                    assertEquals("Сервис платежей недоступен", response.getErrorMessage());
                })
                .verifyComplete();
    }

    @Test
    void changeItemsCount_ShouldCreateCartAndItem_WhenCartDoesNotExistAndActionIsPlus() {
        Long productId = 10L;
        Long userId = 1L;
        Long newCartId = 555L;

        Product mockProduct = new Product();
        mockProduct.setId(productId);

        Mockito.when(productRepository.findById(productId)).thenReturn(Mono.just(mockProduct));
        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.empty());

        Mockito.when(cartRepository.save(Mockito.any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            cart.setId(newCartId);
            return Mono.just(cart);
        });

        CartItem mockSavedItem = new CartItem(null, newCartId, productId, 1, null);
        Mockito.when(cartItemRepository.save(Mockito.any(CartItem.class))).thenReturn(Mono.just(mockSavedItem));
        Mono<Void> result = cartService.changeItemsCount(productId, "PLUS", userId);

        StepVerifier.create(result).verifyComplete();

        Mockito.verify(cartRepository, Mockito.times(1)).save(Mockito.any(Cart.class));
        Mockito.verify(cartItemRepository, Mockito.times(1)).save(Mockito.any(CartItem.class));
    }

    @Test
    void changeItemsCount_ShouldCreateNewItemInExistingCart_WhenItemDoesNotExistAndActionIsPlus() {
        Long productId = 10L;
        Long userId = 1L;
        Long cartId = 100L;

        Product mockProduct = new Product();
        mockProduct.setId(productId);

        Cart mockCart = new Cart();
        mockCart.setId(cartId);

        Mockito.when(productRepository.findById(productId)).thenReturn(Mono.just(mockProduct));
        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));

        Mockito.when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.empty());

        CartItem mockNewItem = new CartItem(null, cartId, productId, 1, null);
        Mockito.when(cartItemRepository.save(Mockito.any(CartItem.class))).thenReturn(Mono.just(mockNewItem));

        Mono<Void> result = cartService.changeItemsCount(productId, "PLUS", userId);

        StepVerifier.create(result).verifyComplete();

        Mockito.verify(cartRepository, Mockito.never()).save(Mockito.any(Cart.class));
        Mockito.verify(cartItemRepository, Mockito.times(1)).save(Mockito.any(CartItem.class));
    }

    @Test
    void changeItemsCount_ShouldIncreaseQuantity_WhenItemExistsAndActionIsPlus() {
        Long productId = 10L;
        Long userId = 1L;
        Long cartId = 100L;

        Product mockProduct = new Product();
        mockProduct.setId(productId);

        Cart mockCart = new Cart();
        mockCart.setId(cartId);

        CartItem existingItem = new CartItem(1L, cartId, productId, 3, null); // Было 3 штуки

        Mockito.when(productRepository.findById(productId)).thenReturn(Mono.just(mockProduct));
        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(existingItem));
        Mockito.when(cartItemRepository.save(Mockito.any(CartItem.class))).thenReturn(Mono.just(existingItem));

        // Вызов
        Mono<Void> result = cartService.changeItemsCount(productId, "PLUS", userId);

        StepVerifier.create(result).verifyComplete();

        assertEquals(4, existingItem.getQuantity());
        Mockito.verify(cartItemRepository, Mockito.times(1)).save(existingItem);
        Mockito.verify(cartItemRepository, Mockito.never()).delete(Mockito.any(CartItem.class));
    }

    @Test
    void changeItemsCount_ShouldDeleteItem_WhenItemExistsWithQuantityOneAndActionIsMinus() {
        Long productId = 10L;
        Long userId = 1L;
        Long cartId = 100L;

        Product mockProduct = new Product();
        mockProduct.setId(productId);

        Cart mockCart = new Cart();
        mockCart.setId(cartId);

        CartItem existingItem = new CartItem(1L, cartId, productId, 1, null); // Осталась всего 1 штука

        Mockito.when(productRepository.findById(productId)).thenReturn(Mono.just(mockProduct));
        Mockito.when(cartRepository.findByUserId(userId)).thenReturn(Mono.just(mockCart));
        Mockito.when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(existingItem));
        Mockito.when(cartItemRepository.delete(Mockito.any(CartItem.class))).thenReturn(Mono.empty());

        // Вызов
        Mono<Void> result = cartService.changeItemsCount(productId, "MINUS", userId);

        StepVerifier.create(result).verifyComplete();

        Mockito.verify(cartItemRepository, Mockito.times(1)).delete(existingItem);
        Mockito.verify(cartItemRepository, Mockito.never()).save(Mockito.any(CartItem.class));

    }


}
