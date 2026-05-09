package com.minierp.document.api;

import java.util.Locale;
import java.util.Map;

public record PdfRenderRequest(
        String templateName,
        Map<String, Object> variables,
        Locale locale
) {
    public static PdfRenderRequest of(String template, Map<String, Object> vars) {
        return new PdfRenderRequest(template, vars, Locale.FRENCH);
    }

    public static PdfRenderRequest of(String template, Map<String, Object> vars, String localeCode) {
        Locale locale = localeCode != null ? Locale.forLanguageTag(localeCode) : Locale.FRENCH;
        return new PdfRenderRequest(template, vars, locale);
    }
}
