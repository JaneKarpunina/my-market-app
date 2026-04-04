package ru.yandex.practicum.mymarket.advice;


import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.yandex.practicum.mymarket.exception.NumberOutsideOfRangeException;
import ru.yandex.practicum.mymarket.exception.UnsupportedMediaTypeException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({MaxUploadSizeExceededException.class, NumberOutsideOfRangeException.class,
            UnsupportedMediaTypeException.class})
    public String handleExceptionProductUpload(RuntimeException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "addItem";
    }
}
