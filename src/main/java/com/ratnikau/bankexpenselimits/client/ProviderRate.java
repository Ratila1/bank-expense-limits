package com.ratnikau.bankexpenselimits.client;

import com.ratnikau.bankexpenselimits.domain.RateKind;

import java.math.BigDecimal;

public record ProviderRate(BigDecimal unitsPerUsd, RateKind kind) {
}