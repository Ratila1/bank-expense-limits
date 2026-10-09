package com.ratnikau.bankexpenselimits.client;


import com.ratnikau.bankexpenselimits.config.RateProviderProperties;
import com.ratnikau.bankexpenselimits.domain.RateKind;
import com.ratnikau.bankexpenselimits.exception.ExchangeRateProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;


@Slf4j
@Component
@RequiredArgsConstructor
public class TwelveDataRateProvider implements ExchangeRateProvider {

    // Окно поиска назад: покрывает выходные и длинные праздники
    private static final int LOOKBACK_DAYS = 10;

    private final RestClient rateProviderRestClient;
    private final RateProviderProperties props;
    private final MeterRegistry meterRegistry;

    // Получить курс с метриками и retry-логикой
    @Override
    public Optional<ProviderRate> fetch(String currency, LocalDate date) {
        if (!StringUtils.hasText(props.apiKey())) {
            throw new ExchangeRateProviderException("Rate provider API key is not configured", false);
        }
        ExchangeRateProviderException last = null;
        for (int attempt = 1; attempt <= props.maxAttempts(); attempt++) {
            try {
                return timedFetch(currency, date);
            } catch (ExchangeRateProviderException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                last = e;
                log.warn("Rate request failed (attempt {}/{}): {}", attempt, props.maxAttempts(), e.getMessage());
                if (attempt < props.maxAttempts()) {
                    meterRegistry.counter("rates.provider.retries", "provider", "twelvedata").increment();
                    pause(attempt);
                }
            }
        }
        throw last;
    }

    // HTTP-запрос к Twelve Data, парсинг ответа, выбор ближайшего курса ≤ даты
    private Optional<ProviderRate> doFetch(String currency, LocalDate date) {
        TwelveDataTimeSeries body;
        try {
            body = rateProviderRestClient.get()
                    .uri(uri -> uri.path("/time_series")
                            .queryParam("symbol", "USD/" + currency)
                            .queryParam("interval", "1day")
                            .queryParam("start_date", date.minusDays(LOOKBACK_DAYS))
                            .queryParam("end_date", date.plusDays(1))
                            .queryParam("apikey", props.apiKey())
                            .build())
                    .retrieve()
                    .body(TwelveDataTimeSeries.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new ExchangeRateProviderException("HTTP " + status, isRetryable(status));
        } catch (RestClientException e) {
            throw new ExchangeRateProviderException("I/O error: " + e.getClass().getSimpleName(), true);
        }

        if (body == null) {
            throw new ExchangeRateProviderException("Empty response", true);
        }
        if ("error".equalsIgnoreCase(body.status())) {
            int code = body.code() == null ? 500 : body.code();
            throw new ExchangeRateProviderException("Provider error " + code + ": " + body.message(), isRetryable(code));
        }
        if (body.values() == null) {
            return Optional.empty();
        }
        return body.values().stream()
                .map(v -> new Dated(LocalDate.parse(v.datetime().substring(0, 10)), v.close()))
                .filter(d -> !d.date().isAfter(date))
                .max(Comparator.comparing(Dated::date))
                .map(d -> toRate(d, date));
    }

    // Конвертация строкового close в BigDecimal, валидация, определение CLOSE / PREVIOUS_CLOSE
    private ProviderRate toRate(Dated dated, LocalDate requested) {
        BigDecimal close;
        try {
            close = new BigDecimal(dated.close());
        } catch (NumberFormatException e) {
            throw new ExchangeRateProviderException("Malformed close value", false);
        }
        if (close.signum() <= 0) {
            throw new ExchangeRateProviderException("Non-positive close value", false);
        }
        var kind = dated.date().equals(requested) ? RateKind.CLOSE : RateKind.PREVIOUS_CLOSE;
        return new ProviderRate(close, kind);
    }

    // Проверка, является ли HTTP-статус временной ошибкой
    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    // Пауза перед повторной попыткой (линейный backoff).
    private void pause(int attempt) {
        try {
            Thread.sleep(props.backoff().multipliedBy(attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeRateProviderException("Interrupted while waiting to retry", false);
        }
    }

    private record Dated(LocalDate date, String close) {
    }

    // Время и результат каждого обращения к внешнему API (по каждой попытке)
    private Optional<ProviderRate> timedFetch(String currency, LocalDate date) {
        var sample = Timer.start(meterRegistry);
        String outcome = "error";
        try {
            var result = doFetch(currency, date);
            outcome = result.isPresent() ? "success" : "empty";
            return result;
        } finally {
            sample.stop(Timer.builder("rates.provider.requests")
                    .description("Calls to the external exchange rate API")
                    .tag("provider", "twelvedata")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }
}