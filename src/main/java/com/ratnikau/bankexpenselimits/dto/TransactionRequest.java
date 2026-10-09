package com.ratnikau.bankexpenselimits.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratnikau.bankexpenselimits.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Расходная операция клиента")
public record TransactionRequest(
        @Schema(description = "Банковский счёт клиента, 10 цифр", example = "0000000123")
        @JsonProperty("account_from") @NotNull @Pattern(regexp = "\\d{10}") String accountFrom,

        @Schema(description = "Банковский счёт контрагента, 10 цифр", example = "9999999999")
        @JsonProperty("account_to") @NotNull @Pattern(regexp = "\\d{10}") String accountTo,

        @Schema(description = "Валюта счёта, код ISO 4217", example = "KZT")
        @JsonProperty("currency_shortname") @NotNull @Pattern(regexp = "[A-Za-z]{3}") String currencyShortname,

        @Schema(description = "Сумма операции, до 2 знаков после точки", example = "10000.45")
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal sum,

        @Schema(description = "Категория расхода", example = "product")
        @JsonProperty("expense_category") @NotNull Category expenseCategory,

        @Schema(description = "Дата и время операции с часовым поясом, ISO 8601", example = "2022-01-30T00:00:00+06:00")
        @NotNull OffsetDateTime datetime) {
}