package ru.yandex.practicum.mymarket.service;

import org.apache.tika.Tika;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.exception.NumberOutsideOfRangeException;
import ru.yandex.practicum.mymarket.exception.UnsupportedMediaTypeException;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class AddItemService {


    private static final String IMAGES_DIR = "uploads/";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    private final ProductRepository productRepository;

    public AddItemService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Mono<Void> addItem(String title, String description, String price, FilePart imageFile) {

        String filename = UUID.randomUUID() + "_" + imageFile.filename();
        Path filePath = Paths.get(IMAGES_DIR, filename);

        return Mono.fromCallable(() -> convertPrice(price))
                .flatMap(finalPrice -> checkFile(imageFile)
                        .thenReturn(finalPrice))
                .flatMap(finalPrice -> Mono.fromRunnable(() -> {
                    try {
                        Files.createDirectories(Paths.get(IMAGES_DIR));
                    } catch (IOException e) {
                        throw new RuntimeException("Ошибка создания папки");
                    }
                }).subscribeOn(Schedulers.boundedElastic()).thenReturn(finalPrice))
                .flatMap(finalPrice -> imageFile.transferTo(filePath)
                        .thenReturn(finalPrice))
                .flatMap(finalPrice -> {
                    Product product = new Product();
                    product.setTitle(title);
                    product.setDescription(description);
                    product.setPrice((Long)finalPrice);
                    product.setImgPath(filename);
                    return productRepository.save(product);
                })
                .then();
    }

    private Long convertPrice(String price) {
        try {
            BigInteger value = new BigInteger(price);
            long finalPrice;

            if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                throw new NumberOutsideOfRangeException("Слишком большая цена");
            } else if (value.compareTo(BigInteger.ZERO) < 0) {
                throw new NumberOutsideOfRangeException("Цена отрицательная");
            } else {
                finalPrice = value.longValue();
            }


            return finalPrice;
        }
        catch(NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректный формат цены: " + price);
        }
    }

    private Mono<Void> checkFile(FilePart image) {

        Mono<Void> sizeCheck = image.content()
                .map(dataBuffer -> (long) dataBuffer.readableByteCount())
                .reduce(0L, Long::sum)
                .flatMap(size -> {
                    if (size > MAX_FILE_SIZE) {
                        return Mono.error(new MaxUploadSizeExceededException(MAX_FILE_SIZE));
                    }
                    return Mono.empty();
                });

        Mono<Void> typeCheck = DataBufferUtils.join(image.content().take(1))
                .flatMap(dataBuffer -> {
                    try (var is = dataBuffer.asInputStream()) {
                        String detectedType = new Tika().detect(is);
                        if (!detectedType.startsWith("image/")) {
                            return Mono.error(new UnsupportedMediaTypeException("Поддерживаются только типы image/"));
                        }
                        return Mono.empty();
                    } catch (IOException e) {
                        return Mono.error(new RuntimeException("Ошибка проверки файла"));
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                });

        return Flux.concat(sizeCheck, typeCheck).then();
    }
}
