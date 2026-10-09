package com.ratnikau.bankexpenselimits.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class MutableClock extends Clock {

    private volatile Instant current;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.current = start;
        this.zone = zone;
    }

    public void set(String isoInstant) {
        this.current = Instant.parse(isoInstant);
    }

    @Override
    public Instant instant() {
        return current;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(current, zone);
    }
}