package com.ratnikau.bankexpenselimits.mapper;

import com.ratnikau.bankexpenselimits.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class TimeMapper {

    private final AppProperties props;

    public OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atZone(props.zone()).toOffsetDateTime();
    }

    public Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }
}