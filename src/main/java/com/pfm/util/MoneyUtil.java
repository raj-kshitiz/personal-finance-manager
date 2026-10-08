package com.pfm.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helpers for monetary values. All money in this application is {@link BigDecimal} with scale 2 and
 * {@link RoundingMode#HALF_UP}.
 */
public final class MoneyUtil {

    /** Scale used for every monetary value and percentage. */
    public static final int SCALE = 2;

    private MoneyUtil() {
    }

    /**
     * Normalises an aggregate for JSON output: an exact zero is emitted as {@code 0}, any other
     * value is scaled to two decimals. The official test script expects {@code "netSavings": 0} for
     * empty periods and {@code "netSavings": 6550.00} otherwise, so the distinction is deliberate.
     *
     * @param value the value to normalise, may be {@code null}
     * @return {@link BigDecimal#ZERO} or the value scaled to two decimals
     */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Scales a monetary value to two decimals, treating {@code null} as zero.
     *
     * @param value the value to scale, may be {@code null}
     * @return the value scaled to two decimals
     */
    public static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Replaces {@code null} with {@link BigDecimal#ZERO}.
     *
     * @param value the value, may be {@code null}
     * @return a non-null value
     */
    public static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
