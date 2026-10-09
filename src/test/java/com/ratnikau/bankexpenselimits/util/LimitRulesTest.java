package com.ratnikau.bankexpenselimits.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LimitRulesTest {

    // Проверяет отсутствие превышения при сумме ниже лимита
    @Test
    void isExceeded_returnsFalse_whenTotalIsBelowLimit() {
        boolean exceeded = LimitRules.isExceeded(bd("0"), bd("500.00"), bd("1000.00"));

        assertThat(exceeded).isFalse();
    }

    // Проверяет отсутствие превышения при нулевом остатке
    @Test
    void isExceeded_returnsFalse_whenRemainderIsExactlyZero() {
        boolean exceeded = LimitRules.isExceeded(bd("500.00"), bd("500.00"), bd("1000.00"));

        assertThat(exceeded).isFalse();
    }

    // Проверяет отсутствие превышения при равенстве первой транзакции лимиту
    @Test
    void isExceeded_returnsFalse_whenFirstTransactionEqualsLimit() {
        boolean exceeded = LimitRules.isExceeded(bd("0"), bd("1000.00"), bd("1000.00"));

        assertThat(exceeded).isFalse();
    }

    // Проверяет превышение лимита на один цент
    @Test
    void isExceeded_returnsTrue_whenOverLimitByOneCent() {
        boolean exceeded = LimitRules.isExceeded(bd("500.00"), bd("500.01"), bd("1000.00"));

        assertThat(exceeded).isTrue();
    }

    // Проверяет сохранение превышения после превышения лимита ранее
    @Test
    void isExceeded_returnsTrue_whenLimitWasAlreadyExceededEarlier() {
        boolean exceeded = LimitRules.isExceeded(bd("1200.00"), bd("1.00"), bd("1000.00"));

        assertThat(exceeded).isTrue();
    }

    // Проверяет отсутствие превышения после увеличения лимита
    @Test
    void isExceeded_returnsFalse_whenLimitWasRaisedAboveSpentAmount() {
        boolean exceeded = LimitRules.isExceeded(bd("1100.00"), bd("100.00"), bd("2000.00"));

        assertThat(exceeded).isFalse();
    }

    // Проверяет превышение после уменьшения лимита ниже уже потраченной суммы
    @Test
    void isExceeded_returnsTrue_whenLimitWasLoweredBelowSpentAmount() {
        boolean exceeded = LimitRules.isExceeded(bd("600.00"), bd("100.00"), bd("400.00"));

        assertThat(exceeded).isTrue();
    }

    // Проверяет сравнение BigDecimal по значению без учёта масштаба
    @Test
    void isExceeded_comparesByValue_notByScale() {
        assertThat(LimitRules.isExceeded(bd("0"), bd("1000"), bd("1000.00"))).isFalse();
        assertThat(LimitRules.isExceeded(bd("0.00"), bd("1000.00"), bd("1000"))).isFalse();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}