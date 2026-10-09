package com.ratnikau.bankexpenselimits.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratnikau.bankexpenselimits.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Запрос на установку нового месячного лимита")
public record LimitRequest(
        @Schema(description = "Банковский счёт клиента, 10 цифр", example = "0000000123")
        @NotNull @Pattern(regexp = "\\d{10}") String account,

        @Schema(description = "Категория расхода", example = "product")
        @JsonProperty("expense_category") @NotNull Category expenseCategory,

        @Schema(description = "Месячный лимит в USD", example = "1000.00")
        @JsonProperty("limit_sum") @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal limitSum) {
}