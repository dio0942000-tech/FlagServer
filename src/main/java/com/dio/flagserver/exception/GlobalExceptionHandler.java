package com.dio.flagserver.exception;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> handleIllegalArgument(IllegalArgumentException exception){
        return Map.of("message",exception.getMessage());
    }
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> handleDuplicateKey(DuplicateKeyException exception){
        return Map.of("message",exception.getMessage());
    }
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String,String> handleNoSuchElementException(NoSuchElementException noSuchElementException){
        return Map.of("message",noSuchElementException.getMessage());
    }
    @ExceptionHandler(RuntimeException.class)
    public Map<String,String> handleRuntimeException(RuntimeException runtimeException){
        return Map.of("message","测试异常");
    }
}
