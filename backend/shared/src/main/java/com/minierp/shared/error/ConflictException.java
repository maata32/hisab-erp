package com.minierp.shared.error;

import java.util.Map;

public class ConflictException extends BusinessException {

    public ConflictException(String messageKey) {
        super(messageKey);
    }

    public ConflictException(String messageKey, Map<String, Object> args) {
        super(messageKey, args);
    }
}
