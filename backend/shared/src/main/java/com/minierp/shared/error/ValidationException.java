package com.minierp.shared.error;

import java.util.Map;

public class ValidationException extends BusinessException {

    public ValidationException(String messageKey) {
        super(messageKey);
    }

    public ValidationException(String messageKey, Map<String, Object> args) {
        super(messageKey, args);
    }
}
