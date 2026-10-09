package com.ratnikau.bankexpenselimits.util;

import java.time.Instant;
import java.time.ZoneId;

// Границы календарного месяца [from, to) в заданном часовом поясе
public record MonthRange(Instant from, Instant to) {

    public static MonthRange of(Instant moment, ZoneId zone) {
        var firstDay = moment.atZone(zone).toLocalDate().withDayOfMonth(1);
        return new MonthRange(
                firstDay.atStartOfDay(zone).toInstant(),
                firstDay.plusMonths(1).atStartOfDay(zone).toInstant());
    }
}