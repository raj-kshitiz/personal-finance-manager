package com.pfm.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MoneyUtil}. */
class MoneyUtilTest {

    @Test
    @DisplayName("normalize emits a bare zero for any exact zero, whatever its scale")
    void normalizeZero() {
        assertThat(MoneyUtil.normalize(null)).isEqualTo(BigDecimal.ZERO);
        assertThat(MoneyUtil.normalize(new BigDecimal("0.00"))).isEqualTo(BigDecimal.ZERO);
        assertThat(MoneyUtil.normalize(BigDecimal.ZERO).toPlainString()).isEqualTo("0");
    }

    @Test
    @DisplayName("normalize scales non-zero values to two decimals")
    void normalizeNonZero() {
        assertThat(MoneyUtil.normalize(new BigDecimal("6550")).toPlainString()).isEqualTo("6550.00");
        assertThat(MoneyUtil.normalize(new BigDecimal("-12.345")).toPlainString()).isEqualTo("-12.35");
    }

    @Test
    @DisplayName("scale always produces two decimals, null means zero")
    void scale() {
        assertThat(MoneyUtil.scale(null).toPlainString()).isEqualTo("0.00");
        assertThat(MoneyUtil.scale(new BigDecimal("5000")).toPlainString()).isEqualTo("5000.00");
    }

    @Test
    @DisplayName("nullToZero replaces null")
    void nullToZero() {
        assertThat(MoneyUtil.nullToZero(null)).isEqualTo(BigDecimal.ZERO);
        assertThat(MoneyUtil.nullToZero(BigDecimal.TEN)).isEqualTo(BigDecimal.TEN);
    }
}
