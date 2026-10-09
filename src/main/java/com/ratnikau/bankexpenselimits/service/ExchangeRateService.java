package com.ratnikau.bankexpenselimits.service;


import com.ratnikau.bankexpenselimits.client.ExchangeRateProvider;
import com.ratnikau.bankexpenselimits.client.ProviderRate;
import com.ratnikau.bankexpenselimits.domain.ExchangeRate;
import com.ratnikau.bankexpenselimits.exception.ExchangeRateProviderException;
import com.ratnikau.bankexpenselimits.repository.ExchangeRateRepository;
import com.ratnikau.bankexpenselimits.util.UsdConverter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository rates;
    private final ExchangeRateProvider provider;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    // Единиц валюты в одном USD: своя БД, затем внешний API, затем последний известный курс
    public Optional<BigDecimal> findUnitsPerUsd(String currency, LocalDate date) {
        if (UsdConverter.USD.equals(currency)) {
            return Optional.of(BigDecimal.ONE);
        }
        var cached = rates.findByCurrencyAndRateDate(currency, date);
        if (cached.isPresent()) {
            return counted("db", cached);
        }
        var fetched = fetchAndStore(currency, date);
        if (fetched.isPresent()) {
            return counted("provider", fetched);
        }
        var fallback = rates.findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(currency, date);
        return counted(fallback.isPresent() ? "fallback" : "none", fallback);
    }

    // Посчитать метрику rates.lookups по источнику (db/provider/fallback/none) и вернуть курс
    private Optional<BigDecimal> counted(String source, Optional<ExchangeRate> rate) {
        meterRegistry.counter("rates.lookups", "source", source).increment();
        return rate.map(ExchangeRate::getRate);
    }

    // Получить курс из внешнего API и сохранить в БД, при ошибке вернуть пустой Optional
    private Optional<ExchangeRate> fetchAndStore(String currency, LocalDate date) {
        try {
            return provider.fetch(currency, date).map(rate -> store(currency, date, rate));
        } catch (ExchangeRateProviderException e) {
            log.warn("Rate provider unavailable for {} on {}: {}", currency, date, e.getMessage());
            return Optional.empty();
        }
    }

    // Сохранить курс в БД, при конфликте уникальности вернуть уже сохранённую запись
    private ExchangeRate store(String currency, LocalDate date, ProviderRate fetched) {
        var rate = new ExchangeRate();
        rate.setCurrency(currency);
        rate.setRateDate(date);
        rate.setRate(fetched.unitsPerUsd().setScale(8, RoundingMode.HALF_UP));
        rate.setRateKind(fetched.kind());
        rate.setFetchedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        log.info("Exchange rate stored: currency={}, date={}, rate={}, kind={}",
                currency, date, rate.getRate(), rate.getRateKind());
        try {
            return rates.saveAndFlush(rate);
        } catch (DataIntegrityViolationException e) {
            // Параллельный запрос успел сохранить тот же курс: берём его
            return rates.findByCurrencyAndRateDate(currency, date).orElseThrow(() -> e);
        }
    }
}