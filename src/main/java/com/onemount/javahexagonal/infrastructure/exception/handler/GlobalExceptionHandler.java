package com.onemount.javahexagonal.infrastructure.exception.handler;

import com.onemount.javahexagonal.application.dto.response.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ExceptionResolve exceptionResolve;

    public GlobalExceptionHandler(ExceptionResolve exceptionResolve) {
        this.exceptionResolve = exceptionResolve;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        return exceptionResolve.resolveBindException(ex);
    }
}
