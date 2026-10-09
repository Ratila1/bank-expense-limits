package com.ratnikau.bankexpenselimits.repository;

import com.ratnikau.bankexpenselimits.AbstractIntegrationTest;
import com.ratnikau.bankexpenselimits.domain.ExchangeRate;
import com.ratnikau.bankexpenselimits.domain.RateKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRateRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ExchangeRateRepository repository;

    // Проверяет получение курса за точную дату
    @Test
    void findByCurrencyAndRateDate_returnsRateOfExactDay() {
        save("KZT", "2022-01-03", "480");

        Optional<ExchangeRate> result = repository.findByCurrencyAndRateDate("KZT", LocalDate.parse("2022-01-03"));

        assertThat(result).hasValueSatisfying(r -> assertThat(r.getRate()).isEqualByComparingTo("480"));
    }

    // Проверяет пустой результат для другой даты
    @Test
    void findByCurrencyAndRateDate_returnsEmpty_forAnotherDay() {
        save("KZT", "2022-01-03", "480");

        Optional<ExchangeRate> result = repository.findByCurrencyAndRateDate("KZT", LocalDate.parse("2022-01-04"));

        assertThat(result).isEmpty();
    }

    // Проверяет получение последнего известного курса до заданной даты
    @Test
    void findFirstLessThanEqual_returnsLatestKnownRateUpToGivenDay() {
        save("KZT", "2022-01-03", "480");
        save("KZT", "2022-01-05", "485");

        Optional<ExchangeRate> result = repository
                .findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc("KZT", LocalDate.parse("2022-01-04"));

        assertThat(result).hasValueSatisfying(r -> assertThat(r.getRate()).isEqualByComparingTo("480"));
    }

    // Проверяет приоритет курса, установленного в тот же день
    @Test
    void findFirstLessThanEqual_returnsRateOfTheSameDay_whenItExists() {
        save("KZT", "2022-01-03", "480");
        save("KZT", "2022-01-05", "485");

        Optional<ExchangeRate> result = repository
                .findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc("KZT", LocalDate.parse("2022-01-05"));

        assertThat(result).hasValueSatisfying(r -> assertThat(r.getRate()).isEqualByComparingTo("485"));
    }

    // Проверяет пустой результат, если все курсы датированы позднее
    @Test
    void findFirstLessThanEqual_returnsEmpty_whenAllRatesAreNewer() {
        save("KZT", "2022-01-05", "485");

        Optional<ExchangeRate> result = repository
                .findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc("KZT", LocalDate.parse("2022-01-04"));

        assertThat(result).isEmpty();
    }

    // Проверяет раздельный поиск курсов для разных валют
    @Test
    void findFirstLessThanEqual_doesNotMixCurrencies() {
        save("RUB", "2022-01-03", "75");

        Optional<ExchangeRate> result = repository
                .findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc("KZT", LocalDate.parse("2022-01-04"));

        assertThat(result).isEmpty();
    }

    // Проверяет запрет дублирования курса для одной валюты и даты
    @Test
    void save_rejectsSecondRateForSameCurrencyAndDay() {
        save("KZT", "2022-01-03", "480");

        assertThatThrownBy(() -> save("KZT", "2022-01-03", "481"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void save(String currency, String date, String rate) {
        var entity = new ExchangeRate();
        entity.setCurrency(currency);
        entity.setRateDate(LocalDate.parse(date));
        entity.setRate(new BigDecimal(rate));
        entity.setRateKind(RateKind.CLOSE);
        entity.setFetchedAt(Instant.parse("2022-01-05T00:00:00Z"));
        repository.saveAndFlush(entity);
    }
}