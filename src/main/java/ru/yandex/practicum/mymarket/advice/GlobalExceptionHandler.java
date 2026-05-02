package ru.yandex.practicum.mymarket.advice;


import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.exception.NumberOutsideOfRangeException;
import ru.yandex.practicum.mymarket.exception.UnsupportedMediaTypeException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({MaxUploadSizeExceededException.class, NumberOutsideOfRangeException.class,
            UnsupportedMediaTypeException.class, IllegalArgumentException.class})
    public Mono<String> handleExceptionProductUpload(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());

        return Mono.just("addItem");
    }
}
