package com.resumerank.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static record ErrorResponse(
            String error,
            String detail,
            int status,
            String timestamp
    ) {
        public ErrorResponse(String error, String detail, int status) {
            this(error, detail, status, Instant.now().toString());
        }
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex) {
        logger.warn("EmailAlreadyExistsException: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("Conflict", ex.getMessage(), HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        logger.warn("BadCredentialsException: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("Unauthorized", ex.getMessage(), HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler({InvalidTokenException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequestExceptions(Exception ex) {
        logger.warn("Bad Request Exception: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("Bad Request", ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        logger.warn("ResourceNotFoundException triggered: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse("Not Found", "Resource not found", HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        logger.warn("Validation failed: {}", detail);
        ErrorResponse body = new ErrorResponse("Validation Failed", detail, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        logger.error("Unhandled exception occurred:", ex);
        ErrorResponse body = new ErrorResponse("Internal Server Error", "Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
