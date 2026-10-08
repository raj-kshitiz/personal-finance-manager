package com.pfm.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pfm.dto.request.TransactionFilter;
import com.pfm.entity.CategoryType;
import com.pfm.exception.BadRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TransactionFilter#parse}. */
class TransactionFilterTest {

    @Test
    @DisplayName("blank and null parameters become null components")
    void emptyFilter() {
        TransactionFilter filter = TransactionFilter.parse(null, "  ", null, "", null);
        assertThat(filter.startDate()).isNull();
        assertThat(filter.endDate()).isNull();
        assertThat(filter.categoryId()).isNull();
        assertThat(filter.categoryName()).isNull();
        assertThat(filter.type()).isNull();
    }

    @Test
    @DisplayName("valid parameters are parsed, type is case-insensitive")
    void fullFilter() {
        TransactionFilter filter = TransactionFilter.parse("2024-01-01", "2024-01-31", "7", " Salary ", "income");
        assertThat(filter.startDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(filter.endDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        assertThat(filter.categoryId()).isEqualTo(7L);
        assertThat(filter.categoryName()).isEqualTo("Salary");
        assertThat(filter.type()).isEqualTo(CategoryType.INCOME);
    }

    @Test
    @DisplayName("malformed values are rejected with 400-mapped exceptions")
    void invalidValues() {
        assertThatThrownBy(() -> TransactionFilter.parse("01-17-2024", null, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("startDate");
        assertThatThrownBy(() -> TransactionFilter.parse(null, "nope", null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("endDate");
        assertThatThrownBy(() -> TransactionFilter.parse(null, null, "abc", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("categoryId");
        assertThatThrownBy(() -> TransactionFilter.parse(null, null, null, null, "TRANSFER"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("INCOME");
    }

    @Test
    @DisplayName("startDate after endDate is rejected")
    void invertedRange() {
        assertThatThrownBy(() -> TransactionFilter.parse("2024-02-01", "2024-01-01", null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be after");
    }
}
