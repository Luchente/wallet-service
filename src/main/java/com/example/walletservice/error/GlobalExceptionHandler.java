package com.example.walletservice.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleWalletNotFound(WalletNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        "WALLET_NOT_FOUND",
                        "Кошелёк не найден",
                        Instant.now(),
                        request.getRequestURI(),
                        Map.of("walletId", String.valueOf(ex.getWalletId()))
                ));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "INSUFFICIENT_FUNDS",
                        "Недостаточно средств",
                        Instant.now(),
                        request.getRequestURI(),
                        Map.of("walletId", String.valueOf(ex.getWalletId()))
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        List<String> supported = ex.getSupportedHttpMethods() == null
                ? List.of()
                : ex.getSupportedHttpMethods().stream().map(Object::toString).toList();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiErrorResponse(
                        "METHOD_NOT_ALLOWED",
                        "Метод не поддерживается для этого эндпоинта",
                        Instant.now(),
                        request.getRequestURI(),
                        Map.of(
                                "method", ex.getMethod(),
                                "supportedMethods", supported
                        )
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = new ArrayList<>();

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(Map.of(
                    "field", fe.getField(),
                    "message", Optional.ofNullable(fe.getDefaultMessage()).orElse("Ошибка валидации")
            ));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "Ошибка валидации запроса",
                        Instant.now(),
                        request.getRequestURI(),
                        Map.of("fieldErrors", fieldErrors)
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> violations = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "Ошибка валидации запроса",
                        Instant.now(),
                        request.getRequestURI(),
                        Map.of("violations", violations)
                ));
    }

    /**
     * Например: /wallets/not-a-uuid
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("parameter", ex.getName());
        details.put("value", String.valueOf(ex.getValue()));
        details.put("expectedType", ex.getRequiredType() == null ? "unknown" : ex.getRequiredType().getSimpleName());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_VALUE",
                        "Некорректное значение параметра",
                        Instant.now(),
                        request.getRequestURI(),
                        details
                ));
    }

    /**
     * Единый формат 404 на неизвестный эндпоинт.
     * Требует:
     * spring.mvc.throw-exception-if-no-handler-found=true
     * spring.web.resources.add-mappings=false
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        "NOT_FOUND",
                        "Эндпоинт не найден",
                        Instant.now(),
                        request.getRequestURI(),
                        Map.of(
                                "method", ex.getHttpMethod(),
                                "path", ex.getRequestURL()
                        )
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable root = rootCause(ex);

        if (root instanceof InvalidFormatException ife) {
            String field = "unknown";
            var path = ife.getPath();
            if (path != null && !path.isEmpty()) {
                field = path.get(path.size() - 1).getFieldName();
            }

            Class<?> targetType = ife.getTargetType();
            List<String> allowedValues =
                    (targetType != null && targetType.isEnum())
                            ? Arrays.stream(targetType.getEnumConstants()).map(Object::toString).toList()
                            : List.of();

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("field", field);
            details.put("value", String.valueOf(ife.getValue()));
            details.put("expectedType", targetType == null ? "unknown" : targetType.getSimpleName());
            if (!allowedValues.isEmpty()) {
                details.put("allowedValues", allowedValues);
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiErrorResponse(
                            "INVALID_VALUE",
                            "Некорректное значение в JSON",
                            Instant.now(),
                            request.getRequestURI(),
                            details
                    ));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_JSON",
                        "Некорректный JSON",
                        Instant.now(),
                        request.getRequestURI(),
                        Map.of()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка сервера",
                        Instant.now(),
                        request.getRequestURI(),
                        Map.of()
                ));
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}