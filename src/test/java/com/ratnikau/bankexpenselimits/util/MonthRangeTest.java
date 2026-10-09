package com.ratnikau.bankexpenselimits.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MonthRangeTest {

    // Проверяет начало текущего месяца и начало следующего месяца
    @Test
    void of_returnsFirstDayOfMonthAndFirstDayOfNextMonth() {
        MonthRange range = MonthRange.of(utc("2022-01-15T10:00:00Z"), ZoneOffset.UTC);

        assertThat(range.from()).isEqualTo(utc("2022-01-01T00:00:00Z"));
        assertThat(range.to()).isEqualTo(utc("2022-02-01T00:00:00Z"));
    }

    // Проверяет включение первого момента месяца
    @Test
    void of_includesTheVeryFirstMomentOfMonth() {
        MonthRange range = MonthRange.of(utc("2022-01-01T00:00:00Z"), ZoneOffset.UTC);

        assertThat(range.from()).isEqualTo(utc("2022-01-01T00:00:00Z"));
    }

    // Проверяет включение последнего момента января в январский диапазон
    @Test
    void of_keepsLastMillisecondOfJanuaryInJanuary() {
        MonthRange range = MonthRange.of(utc("2022-01-31T23:59:59.999Z"), ZoneOffset.UTC);

        assertThat(range.to()).isEqualTo(utc("2022-02-01T00:00:00Z"));
    }

    // Проверяет переход к февралю с его первого момента
    @Test
    void of_movesFirstMomentOfFebruaryToFebruary() {
        MonthRange range = MonthRange.of(utc("2022-02-01T00:00:00Z"), ZoneOffset.UTC);

        assertThat(range.from()).isEqualTo(utc("2022-02-01T00:00:00Z"));
        assertThat(range.to()).isEqualTo(utc("2022-03-01T00:00:00Z"));
    }

    // Проверяет переход декабря к январю следующего года
    @Test
    void of_endsDecemberInTheNextYear() {
        MonthRange range = MonthRange.of(utc("2022-12-20T00:00:00Z"), ZoneOffset.UTC);

        assertThat(range.to()).isEqualTo(utc("2023-01-01T00:00:00Z"));
    }

    // Проверяет корректную обработку високосного года
    @Test
    void of_knowsAboutLeapYear() {
        MonthRange range = MonthRange.of(utc("2024-02-10T00:00:00Z"), ZoneOffset.UTC);

        assertThat(range.to()).isEqualTo(utc("2024-03-01T00:00:00Z"));
    }

    // Проверяет расчёт диапазона с учётом указанного часового пояса
    @Test
    void of_usesGivenZone_soLateEveningOfJanuaryInUtcIsAlreadyFebruaryInPlusSix() {
        MonthRange range = MonthRange.of(utc("2022-01-31T20:00:00Z"), ZoneId.of("+06:00"));

        assertThat(range.from()).isEqualTo(utc("2022-01-31T18:00:00Z"));
        assertThat(range.to()).isEqualTo(utc("2022-02-28T18:00:00Z"));
    }

    // Проверяет сокращение месяца на час при переходе на летнее время
    @Test
    void of_isOneHourShorterInMonthWhenSummerTimeStarts() {
        ZoneId amsterdam = ZoneId.of("Europe/Amsterdam");

        MonthRange march = MonthRange.of(utc("2022-03-15T00:00:00Z"), amsterdam);

        assertThat(march.from()).isEqualTo(utc("2022-02-28T23:00:00Z"));
        assertThat(Duration.between(march.from(), march.to())).isEqualTo(Duration.ofDays(31).minusHours(1));
    }

    // Проверяет отсутствие разрывов и пересечений между соседними месяцами
    @Test
    void of_makesAdjacentMonthsTouchWithoutGapOrOverlap() {
        ZoneId amsterdam = ZoneId.of("Europe/Amsterdam");

        MonthRange march = MonthRange.of(utc("2022-03-15T00:00:00Z"), amsterdam);
        MonthRange april = MonthRange.of(utc("2022-04-15T00:00:00Z"), amsterdam);

        assertThat(march.to()).isEqualTo(april.from());
    }

    private static Instant utc(String value) {
        return Instant.parse(value);
    }
}