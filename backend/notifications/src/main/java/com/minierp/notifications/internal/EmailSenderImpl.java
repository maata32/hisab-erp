package com.minierp.notifications.internal;

import com.minierp.notifications.api.EmailSender;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
class EmailSenderImpl implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@minierp.local}")
    private String defaultFrom;

    @Override
    public void sendText(String to, String subject, String text) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(defaultFrom);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(text);
        mailSender.send(msg);
        log.debug("Sent text email to={} subject={}", to, subject);
    }

    @Override
    public void send(List<String> to, String subject, String text, String html, Map<String, byte[]> attachments) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(defaultFrom);
            helper.setTo(to.toArray(String[]::new));
            helper.setSubject(subject);
            if (html != null && !html.isBlank()) {
                helper.setText(text == null ? "" : text, html);
            } else {
                helper.setText(text == null ? "" : text);
            }
            if (attachments != null) {
                for (var e : attachments.entrySet()) {
                    helper.addAttachment(e.getKey(), () -> new java.io.ByteArrayInputStream(e.getValue()));
                }
            }
            mailSender.send(mime);
            log.debug("Sent multipart email to={} subject={}", to, subject);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email: " + e.getMessage(), e);
        }
    }
}
