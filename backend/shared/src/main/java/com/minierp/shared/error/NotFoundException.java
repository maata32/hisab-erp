package com.minierp.shared.error;

import java.util.Map;

public class NotFoundException extends BusinessException {

    public NotFoundException(String messageKey) {
        super(messageKey);
    }

    public NotFoundException(String messageKey, Map<String, Object> args) {
        super(messageKey, args);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException("error.notfound", Map.of("entity", entity, "id", String.valueOf(id)));
    }
}
