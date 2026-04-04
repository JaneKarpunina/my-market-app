package ru.yandex.practicum.mymarket.exception;

public class NumberOutsideOfRangeException extends RuntimeException{
    public NumberOutsideOfRangeException(String message) {
        super(message);
    }
}
