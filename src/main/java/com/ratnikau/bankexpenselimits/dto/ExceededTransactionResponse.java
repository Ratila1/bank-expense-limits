package com.ratnikau.bankexpenselimits.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratnikau.bankexpenselimits.domain.Category;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Ответ п.6: поля транзакции + три поля превышенного лимита. */
public record ExceededTransactionResponse(
        @JsonProperty("account_from") String accountFrom,
        @JsonProperty("account_to") String accountTo,
        @JsonProperty("currency_shortname") String currencyShortname,
        BigDecimal sum,
        @JsonProperty("expense_category") Category expenseCategory,
        OffsetDateTime datetime,
                @Schema(description = "Сумма превышенного лимита, USD", example = "1000.00")
        @JsonProperty("limit_sum") BigDecimal limitSum,

        @Schema(description = "Дата и время установления превышенного лимита", example = "2022-01-01T00:00:00Z")
        @JsonProperty("limit_datetime") OffsetDateTime limitDatetime,

        @Schema(description = "Валюта лимита", example = "USD")
        @JsonProperty("limit_currency_shortname") String limitCurrencyShortname) {
}