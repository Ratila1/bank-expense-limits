package com.ratnikau.bankexpenselimits.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratnikau.bankexpenselimits.domain.Category;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LimitResponse(
        String account,
        @JsonProperty("expense_category") Category expenseCategory,
        @JsonProperty("limit_sum") BigDecimal limitSum,
        @JsonProperty("limit_datetime") OffsetDateTime limitDatetime,
        @JsonProperty("limit_currency_shortname") String limitCurrencyShortname) {
}