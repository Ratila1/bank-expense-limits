package com.ratnikau.bankexpenselimits.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class UsdConverter {

    public static final String USD = "USD";

    private UsdConverter() {
    }

    // unitsPerUsd: сколько единиц валюты в одном долларе (например, 480 для KZT)
    public static BigDecimal toUsd(BigDecimal amount, BigDecimal unitsPerUsd) {
        return amount.divide(unitsPerUsd, 2, RoundingMode.HALF_UP);
    }
}