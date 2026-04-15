package ru.yandex.practicum.mymarket.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Utils {

    public static void deleteDirectory(String path) throws IOException {

        Path uploads = Paths.get(path);
        if (Files.exists(uploads)) {
            try (Stream<Path> stream = Files.walk(uploads)) {
                stream
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new RuntimeException("Ошибка при удалении: " + p, e);
                            }
                        });
            }
        }
    }
}

