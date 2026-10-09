package com.ratnikau.bankexpenselimits.service;

import com.ratnikau.bankexpenselimits.client.ExchangeRateProvider;
import com.ratnikau.bankexpenselimits.client.ProviderRate;
import com.ratnikau.bankexpenselimits.domain.ExchangeRate;
import com.ratnikau.bankexpenselimits.domain.RateKind;
import com.ratnikau.bankexpenselimits.exception.ExchangeRateProviderException;
import com.ratnikau.bankexpenselimits.repository.ExchangeRateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final LocalDate DATE = LocalDate.of(2022, 1, 3);
    private static final Instant NOW = Instant.parse("2022-01-05T00:00:00.123456789Z");

    @Mock
    private ExchangeRateRepository rates;
    @Mock
    private ExchangeRateProvider provider;

    private SimpleMeterRegistry meters;
    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        service = new ExchangeRateService(rates, provider, Clock.fixed(NOW, ZoneOffset.UTC), meters);
    }

    // Проверяет возврат единичного курса для USD без обращений к зависимостям
    @Test
    void findUnitsPerUsd_returnsOne_forUsdWithoutAnyLookups() {
        Optional<BigDecimal> rate = service.findUnitsPerUsd("USD", DATE);

        assertThat(rate).hasValue(BigDecimal.ONE);
        verifyNoInteractions(rates, provider);
    }

    // Проверяет получение курса из базы данных без обращения к провайдеру
    @Test
    void findUnitsPerUsd_usesRateFromDb_andDoesNotCallProvider() {
        when(rates.findByCurrencyAndRateDate("KZT", DATE)).thenReturn(Optional.of(rateOf("480")));

        Optional<BigDecimal> rate = service.findUnitsPerUsd("KZT", DATE);

        assertThat(rate).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("480"));
        verifyNoInteractions(provider);
        assertThat(lookups("db")).isEqualTo(1.0);
    }

    // Проверяет получение курса от провайдера и его сохранение при отсутствии в базе
    @Test
    void findUnitsPerUsd_fetchesFromProviderAndStoresRate_whenDbHasNone() {
        when(rates.findByCurrencyAndRateDate("KZT", DATE)).thenReturn(Optional.empty());
        when(provider.fetch("KZT", DATE))
                .thenReturn(Optional.of(new ProviderRate(new BigDecimal("480.5"), RateKind.PREVIOUS_CLOSE)));
        when(rates.saveAndFlush(any(ExchangeRate.class))).thenAnswer(call -> call.getArgument(0));

        Optional<BigDecimal> rate = service.findUnitsPerUsd("KZT", DATE);

        assertThat(rate).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("480.5"));
        ArgumentCaptor<ExchangeRate> saved = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(rates).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCurrency()).isEqualTo("KZT");
        assertThat(saved.getValue().getRateDate()).isEqualTo(DATE);
        assertThat(saved.getValue().getRateKind()).isEqualTo(RateKind.PREVIOUS_CLOSE);
        assertThat(saved.getValue().getRate().scale()).isEqualTo(8);
        assertThat(lookups("provider")).isEqualTo(1.0);
    }

    // Проверяет усечение времени получения курса до микросекунд
    @Test
    void findUnitsPerUsd_storesFetchTimeTruncatedToMicroseconds() {
        when(rates.findByCurrencyAndRateDate("KZT", DATE)).thenReturn(Optional.empty());
        when(provider.fetch("KZT", DATE))
                .thenReturn(Optional.of(new ProviderRate(new BigDecimal("480"), RateKind.CLOSE)));
        when(rates.saveAndFlush(any(ExchangeRate.class))).thenAnswer(call -> call.getArgument(0));

        service.findUnitsPerUsd("KZT", DATE);

        ArgumentCaptor<ExchangeRate> saved = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(rates).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFetchedAt()).isEqualTo(Instant.parse("2022-01-05T00:00:00.123456Z"));
    }

    // Проверяет использование последнего известного курса при ошибке провайдера
    @Test
    void findUnitsPerUsd_usesLastKnownRate_whenProviderFails() {
        when(rates.findByCurrencyAndRateDate("KZT", DATE)).thenReturn(Optional.empty());
        when(provider.fetch("KZT", DATE)).thenThrow(new ExchangeRateProviderException("HTTP 503", true));
        when(rates.findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc("KZT", DATE))
                .thenReturn(Optional.of(rateOf("470")));

        Optional<BigDecimal> rate = service.findUnitsPerUsd("KZT", DATE);

        assertThat(rate).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("470"));
        verify(rates, never()).saveAndFlush(any());
        assertThat(lookups("fallback")).isEqualTo(1.0);
    }

    // Проверяет использование последнего известного курса при отсутствии данных у провайдера
    @Test
    void findUnitsPerUsd_usesLastKnownRate_whenProviderHasNoData() {
        when(rates.findByCurrencyAndRateDate("KZT", DATE)).thenReturn(Optional.empty());
        when(provider.fetch("KZT", DATE)).thenReturn(Optional.empty());
        when(rates.findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc("KZT", DATE))
                .thenReturn(Optional.of(rateOf("470")));

        Optional<BigDecimal> rate = service.findUnitsPerUsd("KZT", DATE);

        assertThat(rate).isPresent();
        assertThat(lookups("fallback")).isEqualTo(1.0);
    }

    // Проверяет пустой результат при отсутствии курса во всех источниках
    @Test
    void findUnitsPerUsd_returnsEmpty_whenNoRateAnywhere() {
        when(rates.findByCurrencyAndRateDate("KZT", DATE)).thenReturn(Optional.empty());
        when(provider.fetch("KZT", DATE)).thenThrow(new ExchangeRateProviderException("HTTP 503", true));
        when(rates.findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc("KZT", DATE))
                .thenReturn(Optional.empty());

        Optional<BigDecimal> rate = service.findUnitsPerUsd("KZT", DATE);

        assertThat(rate).isEmpty();
        assertThat(lookups("none")).isEqualTo(1.0);
    }

    // Проверяет получение курса, сохранённого параллельным запросом
    @Test
    void findUnitsPerUsd_returnsRateSavedByParallelRequest_onUniqueViolation() {
        ExchangeRate savedByOther = rateOf("480");
        when(rates.findByCurrencyAndRateDate("KZT", DATE)).thenReturn(Optional.empty(), Optional.of(savedByOther));
        when(provider.fetch("KZT", DATE))
                .thenReturn(Optional.of(new ProviderRate(new BigDecimal("480"), RateKind.CLOSE)));
        when(rates.saveAndFlush(any(ExchangeRate.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        Optional<BigDecimal> rate = service.findUnitsPerUsd("KZT", DATE);

        assertThat(rate).hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("480"));
    }

    private double lookups(String source) {
        return meters.counter("rates.lookups", "source", source).count();
    }

    private static ExchangeRate rateOf(String value) {
        var rate = new ExchangeRate();
        rate.setRate(new BigDecimal(value));
        return rate;
    }
}