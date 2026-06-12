package ru.yandex.practicum.mymarket.payment.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
