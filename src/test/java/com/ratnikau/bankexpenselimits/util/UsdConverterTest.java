package com.ratnikau.bankexpenselimits.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsdConverterTest {

    // Проверяет деление суммы на курс для получения USD
    @Test
    void toUsd_dividesAmountByRate() {
        BigDecimal usd = UsdConverter.toUsd(bd("240000.00"), bd("480"));

        assertThat(usd).isEqualTo(bd("500.00"));
    }

    // Проверяет округление результата до двух знаков
    @Test
    void toUsd_roundsToTwoDecimals() {
        BigDecimal usd = UsdConverter.toUsd(bd("10000.45"), bd("480"));

        assertThat(usd).isEqualTo(bd("20.83"));
    }

    // Проверяет округление результата по правилу HALF_UP
    @Test
    void toUsd_roundsHalfUp() {
        BigDecimal usd = UsdConverter.toUsd(bd("1.00"), bd("8"));

        assertThat(usd).isEqualTo(bd("0.13"));
    }

    // Проверяет сохранение суммы при курсе, равном единице
    @Test
    void toUsd_keepsAmountWhenRateIsOne() {
        BigDecimal usd = UsdConverter.toUsd(bd("600.00"), BigDecimal.ONE);

        assertThat(usd).isEqualTo(bd("600.00"));
    }

    // Проверяет, что результат всегда имеет два знака после запятой
    @Test
    void toUsd_alwaysReturnsScaleTwo() {
        BigDecimal usd = UsdConverter.toUsd(bd("500"), BigDecimal.ONE);

        assertThat(usd.scale()).isEqualTo(2);
    }

    // Проверяет работу с курсом, содержащим восемь знаков после запятой
    @Test
    void toUsd_worksWithRateHavingEightDecimals() {
        BigDecimal usd = UsdConverter.toUsd(bd("1000.00"), bd("450.12345678"));

        assertThat(usd).isEqualTo(bd("2.22"));
    }

    // Проверяет ошибку при делении на нулевой курс
    @Test
    void toUsd_throwsWhenRateIsZero() {
        assertThatThrownBy(() -> UsdConverter.toUsd(bd("100.00"), BigDecimal.ZERO))
                .isInstanceOf(ArithmeticException.class);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}