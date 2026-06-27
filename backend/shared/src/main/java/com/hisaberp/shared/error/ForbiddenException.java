package com.hisaberp.shared.error;

import java.util.Map;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String messageKey) {
        super(messageKey);
    }

    public ForbiddenException(String messageKey, Map<String, Object> args) {
        super(messageKey, args);
    }
}
