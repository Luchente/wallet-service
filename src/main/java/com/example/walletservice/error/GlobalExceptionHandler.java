package com.example.walletservice.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest req) {
        List<String> supported = ex.getSupportedHttpMethods() == null
                ? List.of()
                : ex.getSupportedHttpMethods().stream().map(m -> m.name()).toList();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                new ApiErrorResponse(
                        "METHOD_NOT_ALLOWED",
                        "Метод не поддерживается для этого эндпоинта",
                        Instant.now(),
                        req.getRequestURI(),
                        Map.of(
                                "method", req.getMethod(),
                                "supportedMethods", supported
                        )
                )
        );
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(WalletNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ApiErrorResponse(
                        "WALLET_NOT_FOUND",
                        "Кошелёк не найден",
                        Instant.now(),
                        req.getRequestURI(),
                        Map.of("walletId", ex.getWalletId().toString())
                )
        );
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficient(InsufficientFundsException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiErrorResponse(
                        "INSUFFICIENT_FUNDS",
                        "Недостаточно средств",
                        Instant.now(),
                        req.getRequestURI(),
                        Map.of("walletId", ex.getWalletId().toString())
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "Некорректные поля запроса",
                        Instant.now(),
                        req.getRequestURI(),
                        Map.of("fieldErrors", fieldErrors)
                )
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest req) {
        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "INVALID_PATH_VARIABLE",
                        "Некорректный параметр в пути",
                        Instant.now(),
                        req.getRequestURI(),
                        Map.of(
                                "name", ex.getName(),
                                "value", String.valueOf(ex.getValue())
                        )
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        if (ex.getCause() instanceof InvalidFormatException ife) {
            return ResponseEntity.badRequest().body(
                    new ApiErrorResponse(
                            "INVALID_VALUE",
                            "Некорректное значение в JSON",
                            Instant.now(),
                            req.getRequestURI(),
                            invalidFormatDetails(ife)
                    )
            );
        }

        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "INVALID_JSON",
                        "Некорректный JSON",
                        Instant.now(),
                        req.getRequestURI(),
                        Map.of()
                )
        );
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleSpringError(ErrorResponseException ex, HttpServletRequest req) {
        int status = ex.getStatusCode().value();
        String detail = ex.getBody() != null ? ex.getBody().getDetail() : null;

        return ResponseEntity.status(status).body(
                new ApiErrorResponse(
                        "REQUEST_ERROR",
                        detail != null ? detail : "Ошибка запроса",
                        Instant.now(),
                        req.getRequestURI(),
                        Map.of("status", status)
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAny(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error: {} {}", req.getMethod(), req.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiErrorResponse(
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка сервера",
                        Instant.now(),
                        req.getRequestURI(),
                        Map.of()
                )
        );
    }

    private Map<String, String> toFieldError(FieldError fe) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("field", fe.getField());
        m.put("message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
        return m;
    }

    private Map<String, Object> invalidFormatDetails(InvalidFormatException ife) {
        Map<String, Object> details = new LinkedHashMap<>();

        details.put("value", String.valueOf(ife.getValue()));

        String field = extractFieldPath(ife.getPath());
        if (field != null && !field.isBlank()) {
            details.put("field", field);
        }

        Class<?> targetType = ife.getTargetType();
        if (targetType != null) {
            details.put("expectedType", targetType.getSimpleName());

            if (targetType.isEnum()) {
                Object[] constants = targetType.getEnumConstants();
                if (constants != null) {
                    List<String> allowed = Arrays.stream(constants).map(Object::toString).toList();
                    details.put("allowedValues", allowed);
                }
            }
        }

        return details;
    }

    private String extractFieldPath(List<JsonMappingException.Reference> path) {
        if (path == null || path.isEmpty()) return null;
        return path.get(path.size() - 1).getFieldName();
    }
}