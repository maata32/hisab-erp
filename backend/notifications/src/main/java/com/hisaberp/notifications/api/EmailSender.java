package com.hisaberp.notifications.api;

import java.util.List;
import java.util.Map;

public interface EmailSender {

    /**
     * Send a plain-text email. Subject and body are used verbatim — no template resolution
     * (callers are expected to render with their own templates if richer content is needed).
     */
    void sendText(String to, String subject, String text);

    /**
     * Send an email with optional HTML body and named attachments (filename → bytes).
     */
    void send(List<String> to, String subject, String text, String html, Map<String, byte[]> attachments);
}
