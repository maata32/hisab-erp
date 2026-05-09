package com.minierp.document.api;

/**
 * Renders a Thymeleaf template to PDF bytes. Exposed to sales, delivery, and payment modules.
 */
public interface DocumentRenderer {
    byte[] renderPdf(PdfRenderRequest request);
}
