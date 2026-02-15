package com.example.walletservice.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ApiErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        // Берём базовые атрибуты, чтобы получить status/path
        Map<String, Object> attrs = super.getErrorAttributes(
                webRequest,
                options.including(ErrorAttributeOptions.Include.MESSAGE)
        );

        int status = (Integer) attrs.getOrDefault("status", 500);
        String path = String.valueOf(attrs.getOrDefault("path", ""));

        String errorCode = mapErrorCode(status);
        String message = mapMessage(status);

        Map<String, Object> details = new LinkedHashMap<>();

        Throwable error = getError(webRequest);
        if (error instanceof HttpRequestMethodNotSupportedException e) {
            details.put("method", requestMethod(webRequest));
            details.put("supportedMethods", String.valueOf(e.getSupportedHttpMethods()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("errorCode", errorCode);
        result.put("message", message);
        result.put("timestamp", Instant.now());
        result.put("path", path);
        result.put("details", details);

        return result;
    }

    private String mapErrorCode(int status) {
        return switch (status) {
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 400 -> "REQUEST_ERROR";
            default -> (status >= 500 ? "INTERNAL_ERROR" : "REQUEST_ERROR");
        };
    }

    private String mapMessage(int status) {
        return switch (status) {
            case 404 -> "Эндпоинт не найден";
            case 405 -> "Метод не поддерживается для этого эндпоинта";
            case 400 -> "Некорректный запрос";
            default -> (status >= 500 ? "Внутренняя ошибка сервера" : "Ошибка запроса");
        };
    }

    private String requestMethod(WebRequest webRequest) {
        if (webRequest instanceof ServletWebRequest swr) {
            HttpServletRequest req = swr.getRequest();
            return req.getMethod();
        }
        return null;
    }
}