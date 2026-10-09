package com.ratnikau.bankexpenselimits.support;

import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.dto.TransactionRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class TestData {

    public static final String ACCOUNT = "0000000123";
    public static final String OTHER_ACCOUNT = "0000000456";
    private static final String COUNTERPARTY = "9999999999";

    private TestData() {
    }

    public static TransactionRequest request(String account, Category category, String currency,
                                             String sum, String datetime) {
        return new TransactionRequest(account, COUNTERPARTY, currency, new BigDecimal(sum),
                category, OffsetDateTime.parse(datetime));
    }

    public static TransactionRequest usd(String account, Category category, String sum, String datetime) {
        return request(account, category, "USD", sum, datetime);
    }
}