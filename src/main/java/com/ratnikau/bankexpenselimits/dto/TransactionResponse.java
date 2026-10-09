package com.ratnikau.bankexpenselimits.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionResponse(
        Long id,
        @JsonProperty("account_from") String accountFrom,
        @JsonProperty("account_to") String accountTo,
        @JsonProperty("currency_shortname") String currencyShortname,
        BigDecimal sum,
        @JsonProperty("expense_category") Category expenseCategory,
        OffsetDateTime datetime,
        @JsonProperty("sum_usd") BigDecimal sumUsd,
        TransactionStatus status,
        @JsonProperty("limit_exceeded") boolean limitExceeded) {
}