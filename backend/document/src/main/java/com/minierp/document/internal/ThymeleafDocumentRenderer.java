package com.minierp.document.internal;

import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.tenant.api.TenantSettingsLookup;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
class ThymeleafDocumentRenderer implements DocumentRenderer {

    private final TemplateEngine templateEngine;
    private final TenantSettingsLookup tenantSettings;

    @Override
    public byte[] renderPdf(PdfRenderRequest request) {
        Context ctx = new Context(request.locale());
        if (request.variables() != null) {
            request.variables().forEach(ctx::setVariable);
        }
        if (!ctx.containsVariable("decimals")) {
            ctx.setVariable("decimals", resolveDecimals());
        }

        String html = templateEngine.process("pdf/" + request.templateName(), ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF rendering failed for template={}", request.templateName(), e);
            throw new RuntimeException("PDF rendering failed: " + e.getMessage(), e);
        }
    }

    private int resolveDecimals() {
        return CurrentUserHolder.tryGet()
                .map(u -> tenantSettings.getCurrencyDecimalPlaces(u.tenantId()))
                .orElse(0);
    }
}
