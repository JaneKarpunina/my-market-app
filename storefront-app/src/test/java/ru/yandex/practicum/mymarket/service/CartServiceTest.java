package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.api.PaymentApi;
import ru.yandex.practicum.mymarket.domain.BalanceResponse;
import ru.yandex.practicum.mymarket.dto.CartDetailedResponse;
import ru.yandex.practicum.mymarket.dto.CartDto;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Cart;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = CartService.class)
public class CartServiceTest extends BaseTest {

    @Autowired
    private CartService cartService;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private PaymentApi paymentApi;

    @MockBean
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ReactiveValueOperations<String, Object> valueOperations;

    private final Long productId = 1L;
    private final String cartId = "test-cart-123";
    private final String PLUS = "PLUS";
    private final String MINUS = "MINUS";
    private final String DELETE = "DELETE";

    @BeforeEach
    void resetMocks() {
        reset(cartRepository, cartItemRepository, productRepository, paymentApi,
                redisTemplate, valueOperations);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getCartDto_ShouldReturnEmptyCart_WhenCartIdIsNullOrEmpty() {

        Mono<CartDto> resultNull = cartService.getCartDto(null);
        Mono<CartDto> resultEmpty = cartService.getCartDto("");

        StepVerifier.create(resultNull)
                .assertNext(cartDto -> {
                    assertTrue(cartDto.getItems().isEmpty());
                    assertEquals(0L, cartDto.getTotal());
                })
                .verifyComplete();

        StepVerifier.create(resultEmpty)
                .assertNext(cartDto -> assertTrue(cartDto.getItems().isEmpty()))
                .verifyComplete();

        verifyNoInteractions(cartRepository, cartItemRepository, productRepository, redisTemplate);
    }

    @Test
    void getCartDto_ShouldReturnEmptyCart_WhenCartHasNoItems() {
        Cart existingCart = new Cart();
        existingCart.setId(cartId);

        when(cartRepository.findById(cartId)).thenReturn(Mono.just(existingCart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.empty()); // Корзина пустая

        Mono<CartDto> result = cartService.getCartDto(cartId);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertTrue(cartDto.getItems().isEmpty());
                    assertEquals(0L, cartDto.getTotal());
                })
                .verifyComplete();

        verify(cartRepository, times(1)).findById(cartId);
        verify(cartItemRepository, times(1)).findByCartId(cartId);
        verifyNoInteractions(productRepository, valueOperations);
    }

    @Test
    void getCartDto_Success_AllProductsFoundInCache() {
        Cart existingCart = new Cart();
        existingCart.setId(cartId);

        CartItem item1 = new CartItem(1L, cartId, 101L, 2, 1L);

        Product product1 = new Product(101L, "Товар 101", "Описание 101",
                "img101.png", 150L, 1L);

        when(cartRepository.findById(cartId)).thenReturn(Mono.just(existingCart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(item1));

        List<String> expectedCacheKeys = List.of("product:101");
        when(valueOperations.multiGet(expectedCacheKeys)).thenReturn(Mono.just(List.of(product1)));

        Mono<CartDto> result = cartService.getCartDto(cartId);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertNotNull(cartDto);
                    assertEquals(1, cartDto.getItems().size());
                    // 150 * 2 = 300
                    assertEquals(300L, cartDto.getTotal());
                    ItemDto itemDto = cartDto.getItems().get(0);
                    assertEquals("Товар 101", itemDto.getTitle());
                    assertEquals(2, itemDto.getCount());
                })
                .verifyComplete();

        verify(productRepository, never()).findAllById(any(Iterable.class));
        verify(valueOperations, never()).multiSet(anyMap());
    }

    @Test
    void getCartDto_Success_CacheMiss_ShouldFetchFromDbAndSaveToRedis() {
        Cart existingCart = new Cart();
        existingCart.setId(cartId);

        CartItem item1 = new CartItem(1L, cartId, 101L, 3, 1L);

        Product product1 = new Product(101L, "Товар 101", "Описание 101",
                "img101.png", 200L, 1L);

        when(cartRepository.findById(cartId)).thenReturn(Mono.just(existingCart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.just(item1));

        List<Object> redisResult = new ArrayList<>();
        redisResult.add(null);
        when(valueOperations.multiGet(List.of("product:101"))).thenReturn(Mono.just(redisResult));

        when(productRepository.findAllById(List.of(101L))).thenReturn(Flux.just(product1));

        when(valueOperations.multiSet(anyMap())).thenReturn(Mono.just(true));
        when(redisTemplate.expire(eq("product:101"), any())).thenReturn(Mono.just(true));

        Mono<CartDto> result = cartService.getCartDto(cartId);
        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertNotNull(cartDto);
                    assertEquals(1, cartDto.getItems().size());
                    // 200 * 3 = 600
                    assertEquals(600L, cartDto.getTotal());
                })
                .verifyComplete();

        // Верифицируем, что сработал Cache Miss сценарий
        verify(productRepository, times(1)).findAllById(List.of(101L));
        verify(valueOperations, times(1)).multiSet(anyMap());
        verify(redisTemplate, times(1)).expire(eq("product:101"), any());

    }

    @Test
    void getCartDto_ShouldCreateNewCart_WhenCartNotFoundInDb() {

        when(cartRepository.findById(cartId)).thenReturn(Mono.empty());

        Cart savedCart = new Cart();
        savedCart.setId(cartId);
        when(cartRepository.save(any(Cart.class))).thenReturn(Mono.just(savedCart));

        when(cartItemRepository.findByCartId(cartId)).thenReturn(Flux.empty());

        Mono<CartDto> result = cartService.getCartDto(cartId);

        StepVerifier.create(result)
                .assertNext(cartDto -> {
                    assertTrue(cartDto.getItems().isEmpty());
                    assertEquals(0L, cartDto.getTotal());
                })
                .verifyComplete();

        verify(cartRepository, times(1)).save(any(Cart.class));
    }


    @Test
    void changeQuantity_DeleteOnMinusOne() {
        CartItem item = new CartItem(10L, cartId, productId, 1, 0L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(item));
        when(cartItemRepository.delete(item)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeItemQuantity(productId, MINUS, cartId))
                .verifyComplete();

        verify(cartItemRepository).delete(item);
    }

    @Test
    void changeQuantity_DeleteOnAction() {
        CartItem item = new CartItem(10L, cartId, productId, 5, 0L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(item));
        when(cartItemRepository.delete(item)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeItemQuantity(productId, DELETE, cartId))
                .verifyComplete();

        verify(cartItemRepository).delete(item);
    }

    @Test
    void changeQuantity_Decrement() {
        CartItem item = new CartItem(10L, cartId, productId, 5, 0L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(item));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(Mono.just(item));

        StepVerifier.create(cartService.changeItemQuantity(productId, MINUS, cartId))
                .verifyComplete();

        verify(cartItemRepository).save(argThat(it -> it.getQuantity() == 4));
    }

    @Test
    void changeQuantity_Increment() {
        CartItem item = new CartItem(10L, cartId, productId, 2, 0L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.just(item));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(Mono.just(item));

        StepVerifier.create(cartService.changeItemQuantity(productId, PLUS, cartId))
                .verifyComplete();

        verify(cartItemRepository).save(argThat(it -> it.getQuantity() == 3));
    }

    @Test
    void changeQuantity_NotFound() {
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Mono.empty());

        StepVerifier.create(cartService.changeItemQuantity(productId, PLUS, cartId))
                .verifyComplete();

        verify(cartItemRepository, never()).save(any());
        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void getCartDetailed_Success_WhenBalanceIsEnough() {

        CartDto mockCart = new CartDto(List.of(), 1000L);

        CartService testService = spy(cartService);
        doReturn(Mono.just(mockCart)).when(testService).getCartDto(cartId);

        BalanceResponse mockBalance = new BalanceResponse();
        mockBalance.setAmount(1500L);
        when(paymentApi.getBalance()).thenReturn(Mono.just(mockBalance));

        Mono<CartDetailedResponse> result = testService.getCartDetailed(cartId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(1000L, response.getCart().getTotal());
                    assertEquals(1500L, response.getBalance());
                    assertTrue(response.isCanOrder());
                    assertNull(response.getErrorMessage());
                })
                .verifyComplete();
    }

    @Test
    void getCartDetailed_Failed_WhenInsufficientFunds() {
        CartDto mockCart = new CartDto(List.of(), 1000L);

        CartService testService = spy(cartService);
        doReturn(Mono.just(mockCart)).when(testService).getCartDto(cartId);

        // Настраиваем маленький баланс (400 < 1000)
        BalanceResponse mockBalance = new BalanceResponse();
        mockBalance.setAmount(400L);
        when(paymentApi.getBalance()).thenReturn(Mono.just(mockBalance));

        Mono<CartDetailedResponse> result = testService.getCartDetailed(cartId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(400L, response.getBalance());
                    assertFalse(response.isCanOrder());
                    assertEquals("Недостаточно средств", response.getErrorMessage());
                })
                .verifyComplete();
    }

    @Test
    void getCartDetailed_Failed_WhenPaymentServiceThrowsException() {
        CartDto mockCart = new CartDto(List.of(), 1000L);

        CartService testService = spy(cartService);
        doReturn(Mono.just(mockCart)).when(testService).getCartDto(cartId);

        // Имитируем сетевую ошибку WebClient (сервис платежей недоступен)
        when(paymentApi.getBalance()).thenReturn(Mono.error(new RuntimeException("Timeout")));

        Mono<CartDetailedResponse> result = testService.getCartDetailed(cartId);

        // Проверяем, что .onErrorReturn(-1L) корректно перехватил ошибку
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(-1L, response.getBalance()); // Ошибка превратилась в -1
                    assertFalse(response.isCanOrder());
                    assertEquals("Сервис платежей недоступен", response.getErrorMessage());
                })
                .verifyComplete();
    }



}
