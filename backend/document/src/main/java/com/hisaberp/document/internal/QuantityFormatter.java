package com.hisaberp.document.internal;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Locale-aware quantity formatter that drops trailing zeros after the decimal
 * separator. Exposed in the Thymeleaf context as {@code qty} by the document
 * renderer, used in templates via {@code ${qty.format(line.quantity)}}.
 *
 * Examples (fr-FR): 3 → "3", 3.50 → "3,5", 3.123 → "3,123", 1000 → "1 000".
 */
public class QuantityFormatter {

    private final Locale locale;

    public QuantityFormatter(Locale locale) {
        this.locale = locale == null ? Locale.getDefault() : locale;
    }

    public String format(BigDecimal value) {
        if (value == null) return "";
        BigDecimal stripped = value.stripTrailingZeros();
        // stripTrailingZeros() on whole numbers yields scale ≤ 0 (e.g. 1E+2 for 100).
        if (stripped.scale() <= 0) {
            return NumberFormat.getIntegerInstance(locale).format(stripped.toBigInteger());
        }
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMinimumFractionDigits(stripped.scale());
        nf.setMaximumFractionDigits(stripped.scale());
        return nf.format(stripped);
    }
}
