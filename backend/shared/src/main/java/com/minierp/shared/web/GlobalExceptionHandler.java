package com.minierp.shared.web;

import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.ForbiddenException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messages;

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, e, req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException e, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, e, req);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> forbidden(ForbiddenException e, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, e, req);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> validation(ValidationException e, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e, req);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> businessFallback(BusinessException e, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> methodArgumentInvalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ApiError.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(
                        fe.getField(),
                        fe.getCode(),
                        fe.getDefaultMessage()))
                .toList();
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                "validation.failed",
                resolve("error.validation"),
                req.getRequestURI(),
                traceId(req),
                fieldErrors,
                Map.of());
        return ResponseEntity.unprocessableEntity().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException e, HttpServletRequest req) {
        List<ApiError.FieldError> fieldErrors = e.getConstraintViolations().stream()
                .map(cv -> new ApiError.FieldError(
                        cv.getPropertyPath().toString(),
                        cv.getMessageTemplate(),
                        cv.getMessage()))
                .toList();
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                "validation.failed",
                resolve("error.validation"),
                req.getRequestURI(),
                traceId(req),
                fieldErrors,
                Map.of());
        return ResponseEntity.unprocessableEntity().body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> badCredentials(BadCredentialsException e, HttpServletRequest req) {
        return simpleError(HttpStatus.UNAUTHORIZED, "auth.bad_credentials", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> auth(AuthenticationException e, HttpServletRequest req) {
        return simpleError(HttpStatus.UNAUTHORIZED, "auth.required", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(AccessDeniedException e, HttpServletRequest req) {
        return simpleError(HttpStatus.FORBIDDEN, "auth.forbidden", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return simpleError(HttpStatus.CONFLICT, "error.data_integrity", req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> optimisticLock(OptimisticLockingFailureException e, HttpServletRequest req) {
        return simpleError(HttpStatus.CONFLICT, "error.optimistic_lock", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return simpleError(HttpStatus.METHOD_NOT_ALLOWED, "error.method_not_allowed", req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> maxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest req) {
        return simpleError(HttpStatus.UNPROCESSABLE_ENTITY, "error.attachment.too_large", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noResource(NoResourceFoundException e, HttpServletRequest req) {
        return simpleError(HttpStatus.NOT_FOUND, "error.resource_not_found", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception [{}] on {} {}", errorId, req.getMethod(), req.getRequestURI(), e);
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "error.internal",
                resolve("error.internal"),
                req.getRequestURI(),
                traceId(req),
                List.of(),
                Map.of("errorId", errorId));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, BusinessException e, HttpServletRequest req) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                e.getMessageKey(),
                resolve(e.getMessageKey(), e.getArgs()),
                req.getRequestURI(),
                traceId(req),
                List.of(),
                e.getArgs());
        return ResponseEntity.status(status).body(body);
    }

    protected ResponseEntity<ApiError> simpleError(HttpStatus status, String code, HttpServletRequest req) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                resolve(code),
                req.getRequestURI(),
                traceId(req),
                List.of(),
                Map.of());
        return ResponseEntity.status(status).body(body);
    }

    private String resolve(String key) {
        return resolve(key, Map.of());
    }

    private String resolve(String key, Map<String, Object> args) {
        try {
            Object[] flat = args.values().toArray();
            return messages.getMessage(key, flat, key, LocaleContextHolder.getLocale());
        } catch (Exception ex) {
            return key;
        }
    }

    private String traceId(HttpServletRequest req) {
        String header = req.getHeader("X-Trace-Id");
        return header != null ? header : "";
    }
}
