package com.dcb.exception;

import com.dcb.util.Util;
import com.dcb.model.APIResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Autowired
    Util util;

    @ExceptionHandler(BankException.class)
    public ResponseEntity<APIResponse<?>> handleBankException(BankException e){
        log.info("Handling BankException, due to: {}",e.getMessage());
        return ResponseEntity.badRequest().body(util.getErrorResponse(null,e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("Method argument not valid exception, due to: {}",e.getMessage());
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(util.getErrorResponse(null, errors.values().toString()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<?>> handleBankException(Exception e){
        log.info("Handling Exception, due to: {}",e.getMessage());
        e.printStackTrace();
        return ResponseEntity.internalServerError().body(util.getErrorResponse(null,e.getMessage()));
    }

}
