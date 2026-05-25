package ru.yandex.practicum.mymarket.payment.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.yandex.practicum.mymarket.payment.domain.ErrorResponse;
import ru.yandex.practicum.mymarket.payment.exception.InsufficientFundsException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsExceptions(InsufficientFundsException ex) {
        return getResponseEntity(ex, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleExceptions(Exception ex) {
        return getResponseEntity(ex, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> getResponseEntity(Exception ex, HttpStatus httpStatus) {
        ErrorResponse error = new ErrorResponse();
        error.setError(ex.getMessage());
        return ResponseEntity.status(httpStatus).body(error);
    }
}
