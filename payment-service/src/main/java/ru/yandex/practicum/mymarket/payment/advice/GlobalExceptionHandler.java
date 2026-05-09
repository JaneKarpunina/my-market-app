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
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        return getResponseEntity(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleExceptions(Exception ex) {
        return getResponseEntity(ex);
    }

    private ResponseEntity<ErrorResponse> getResponseEntity(Exception ex) {
        ErrorResponse error = new ErrorResponse();
        error.setError(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
