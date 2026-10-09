package com.ratnikau.bankexpenselimits.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "rates.provider")
public record RateProviderProperties(
        @DefaultValue("https://api.twelvedata.com") String baseUrl,
        String apiKey,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("300ms") Duration backoff) {
}