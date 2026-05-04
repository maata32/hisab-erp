package com.minierp.shared.error;

import lombok.Getter;

import java.util.Map;

/**
 * Application-level error with an i18n message key and optional template arguments.
 * Subclasses set the HTTP status they map to via the GlobalExceptionHandler.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final String messageKey;
    private final transient Map<String, Object> args;

    protected BusinessException(String messageKey, Map<String, Object> args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args == null ? Map.of() : args;
    }

    protected BusinessException(String messageKey) {
        this(messageKey, Map.of());
    }
}
