package com.ratnikau.bankexpenselimits.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("UTC") ZoneId zone,
        @DefaultValue("1000.00") BigDecimal defaultLimitUsd) {
}