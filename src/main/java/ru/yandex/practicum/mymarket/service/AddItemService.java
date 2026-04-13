package ru.yandex.practicum.mymarket.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.yandex.practicum.mymarket.entity.Product;
import ru.yandex.practicum.mymarket.exception.NumberOutsideOfRangeException;
import ru.yandex.practicum.mymarket.exception.UnsupportedMediaTypeException;
import ru.yandex.practicum.mymarket.repository.ProductRepository;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class AddItemService {


    private static final String IMAGES_DIR = "uploads/";

    private final ProductRepository productRepository;

    public AddItemService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void addItem(String title, String description, String price, MultipartFile imageFile) {

        Long finalPrice = convertPrice(price);
        String filename = UUID.randomUUID() + "_" + imageFile.getOriginalFilename();

        Path filePath = Paths.get(IMAGES_DIR, filename);
        Path uploadDir = Paths.get(IMAGES_DIR);

        try {

            checkFile(imageFile);

            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch(IOException ex) {
           throw new RuntimeException("Не удалось сохранить в файл");
        }


        Product product = new Product();
        product.setTitle(title);
        product.setDescription(description);
        product.setPrice(finalPrice);
        product.setImgPath(filename);

        productRepository.save(product);
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

    private static void checkFile(MultipartFile image) throws IOException {
        String detectedType = new Tika().detect(image.getBytes());
        if (!detectedType.startsWith("image/")) {
            throw new UnsupportedMediaTypeException("Поддерживаются только типы image/");
        }

    }
}
