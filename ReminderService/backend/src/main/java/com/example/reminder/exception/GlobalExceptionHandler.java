package com.example.reminder.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.LinkedHashMap;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e,HttpServletRequest r) { return response(e.getStatus(),e.getMessage(),r); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e,HttpServletRequest r) {
        var fields=new LinkedHashMap<String,String>();
        e.getBindingResult().getFieldErrors().forEach(f->fields.putIfAbsent(f.getField(),f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(),400,"Please check the highlighted fields",r.getRequestURI(),fields));
    }
    @ExceptionHandler({IllegalArgumentException.class,DateTimeException.class,HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> bad(Exception e,HttpServletRequest r) { return response(HttpStatus.BAD_REQUEST,"Invalid request, date, or time zone",r); }
    @ExceptionHandler({IllegalStateException.class,ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiError> conflict(Exception e,HttpServletRequest r) { return response(HttpStatus.CONFLICT,"The reminder changed or cannot be modified in its current state. Reload and try again.",r); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(Exception e,HttpServletRequest r) { return response(HttpStatus.CONFLICT,"A record with these unique values already exists or violates a relationship",r); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(Exception e,HttpServletRequest r) { return response(HttpStatus.FORBIDDEN,"Access denied",r); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(Exception e,HttpServletRequest r) { return response(HttpStatus.METHOD_NOT_ALLOWED,"HTTP method not supported",r); }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(Exception e,HttpServletRequest r) { return response(HttpStatus.NOT_FOUND,"Endpoint not found",r); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e,HttpServletRequest r) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Request failed: {}",e.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR,"An internal error occurred",r);
    }
    private ResponseEntity<ApiError> response(HttpStatus status,String message,HttpServletRequest r) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(),message,r.getRequestURI()));
    }
}
