package com.minierp.document.internal;

import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
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

    @Override
    public byte[] renderPdf(PdfRenderRequest request) {
        Context ctx = new Context(request.locale());
        if (request.variables() != null) {
            request.variables().forEach(ctx::setVariable);
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
}
