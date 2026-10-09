package com.ratnikau.bankexpenselimits.repository;

import com.ratnikau.bankexpenselimits.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByCurrencyAndRateDate(String currency, LocalDate rateDate);

    // Последний известный курс на дату или раньше: запасной вариант при сбое внешнего API
    Optional<ExchangeRate> findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
            String currency, LocalDate rateDate);
}