package com.minierp.document.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuantityFormatter — strip trailing zeros, locale-aware separator")
class QuantityFormatterTest {

    private final QuantityFormatter fr = new QuantityFormatter(Locale.FRENCH);

    @Test void integerScaleZeroPrintsAsInteger() {
        assertThat(fr.format(new BigDecimal("3"))).isEqualTo("3");
    }

    @Test void integerWithTrailingZeroDecimalsPrintsAsInteger() {
        assertThat(fr.format(new BigDecimal("3.000"))).isEqualTo("3");
        assertThat(fr.format(new BigDecimal("3.0"))).isEqualTo("3");
    }

    @Test void decimalKeepsSignificantDigitsOnly() {
        assertThat(fr.format(new BigDecimal("3.5"))).isEqualTo("3,5");
        assertThat(fr.format(new BigDecimal("3.500"))).isEqualTo("3,5");
        assertThat(fr.format(new BigDecimal("3.123"))).isEqualTo("3,123");
    }

    @Test void wholeHundredKeepsTrailingZerosOnIntegerSide() {
        assertThat(fr.format(new BigDecimal("100"))).isEqualTo("100");
        assertThat(fr.format(new BigDecimal("100.00"))).isEqualTo("100");
    }

    @Test void zeroPrintsAsZero() {
        assertThat(fr.format(BigDecimal.ZERO)).isEqualTo("0");
    }

    @Test void nullPrintsEmpty() {
        assertThat(fr.format(null)).isEmpty();
    }
}
