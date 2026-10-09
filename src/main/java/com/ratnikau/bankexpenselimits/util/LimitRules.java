package com.ratnikau.bankexpenselimits.util;

import java.math.BigDecimal;

public final class LimitRules {

    private LimitRules() {
    }

    // Превышение: потрачено за месяц + текущая сумма строго больше лимита. Остаток ровно 0 не превышение
    public static boolean isExceeded(BigDecimal spentBefore, BigDecimal current, BigDecimal limit) {
        return spentBefore.add(current).compareTo(limit) > 0;
    }
}