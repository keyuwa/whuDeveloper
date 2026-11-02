package edu.whu.developer.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> status(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "请求失败" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorBody(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("参数不合法");
        return ResponseEntity.badRequest().body(new ErrorBody(message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(new ErrorBody("参数不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> fallback(Exception ex) {
        log.error("Unhandled request error", ex);
        return ResponseEntity.internalServerError().body(new ErrorBody("服务器错误"));
    }
}
