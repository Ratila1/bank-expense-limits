package com.ratnikau.bankexpenselimits.exception;

import lombok.Getter;

@Getter
public class ExchangeRateProviderException extends RuntimeException {

    private final boolean retryable;

    public ExchangeRateProviderException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }
}