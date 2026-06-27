package com.hisaberp.document.internal;

import com.hisaberp.document.api.DocumentRenderer;
import com.hisaberp.document.api.PdfRenderRequest;
import com.hisaberp.shared.security.CurrentUserHolder;
import com.hisaberp.tenant.api.TenantSettingsLookup;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
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
        String paperSize = resolvePaperSize();
        Context ctx = new Context(request.locale());
        if (request.variables() != null) {
            request.variables().forEach(ctx::setVariable);
        }
        if (!ctx.containsVariable("decimals")) {
            ctx.setVariable("decimals", resolveDecimals());
        }
        ctx.setVariable("paperSize", paperSize);
        ctx.setVariable("qty", new QuantityFormatter(request.locale()));

        String html = templateEngine.process("pdf/" + request.templateName(), ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            applyPaperSize(builder, paperSize);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF rendering failed for template={}", request.templateName(), e);
            throw new RuntimeException("PDF rendering failed: " + e.getMessage(), e);
        }
    }

    private String resolvePaperSize() {
        return CurrentUserHolder.tryGet()
                .map(u -> tenantSettings.getPaperSize(u.tenantId()))
                .orElse("A4");
    }

    private void applyPaperSize(PdfRendererBuilder builder, String size) {
        if ("A5".equals(size)) {
            builder.useDefaultPageSize(148f, 210f, BaseRendererBuilder.PageSizeUnits.MM);
        } else {
            builder.useDefaultPageSize(210f, 297f, BaseRendererBuilder.PageSizeUnits.MM);
        }
    }

    private int resolveDecimals() {
        return CurrentUserHolder.tryGet()
                .map(u -> tenantSettings.getCurrencyDecimalPlaces(u.tenantId()))
                .orElse(0);
    }
}
