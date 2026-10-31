package com.dcb.exception;

import com.dcb.util.Util;
import com.dcb.model.APIResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<APIResponse<?>> handleNotFoundException(NotFoundException e){
        log.info("Handling NotFoundException, due to: {}",e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(util.getErrorResponse(null,e.getMessage()));
    }
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<APIResponse<?>> handleInsufficientFunds(InsufficientFundsException e){
        log.info("Handling InsufficientFundsException, due to: {}",e.getMessage());
        return ResponseEntity.status(422).body(util.getErrorResponse(null,e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("Method argument not valid exception, due to: {}",e.getMessage());
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(util.getErrorResponse(null, errors.values().toString()));
    }
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<APIResponse<?>> handleBadRequestParameters(Exception e){
        log.info("Bad request parameters, due to: {}",e.getMessage());
        String message = e instanceof MissingRequestHeaderException h
                ? h.getHeaderName() + " header is required."
                : e instanceof MethodArgumentTypeMismatchException m
                ? "Invalid value for '" + m.getName() + "'."
                : e.getMessage();
        return ResponseEntity.badRequest().body(util.getErrorResponse(null, message));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIResponse<?>> handleUnreadableBody(HttpMessageNotReadableException e){
        log.info("Unreadable request body, due to: {}",e.getMessage());
        return ResponseEntity.badRequest().body(util.getErrorResponse(null, "Request body is missing or has an invalid value."));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<APIResponse<?>> handleUploadTooLarge(MaxUploadSizeExceededException e){
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(util.getErrorResponse(null, "File is too large."));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<APIResponse<?>> handleAccessDenied(AccessDeniedException e){
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(util.getErrorResponse(null, "You are not allowed to perform this action."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse<?>> handleDataIntegrity(DataIntegrityViolationException e){
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(util.getErrorResponse(null, "Request conflicts with an existing record, it may be a duplicate."));
    }
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<APIResponse<?>> handleNoRoute(Exception e){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(util.getErrorResponse(null, "No such API endpoint."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<?>> handleBankException(Exception e){
        log.error("Handling Exception, due to: {}",e.getMessage(), e);
        return ResponseEntity.internalServerError().body(util.getErrorResponse(null,"Something went wrong, please try again later."));
    }

}
