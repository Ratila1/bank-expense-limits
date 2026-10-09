package com.ratnikau.bankexpenselimits.config;

import com.ratnikau.bankexpenselimits.domain.TransactionStatus;
import com.ratnikau.bankexpenselimits.repository.TransactionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    // Растёт, когда внешний API недоступен: сигнал для мониторинга
    @Bean
    public MeterBinder pendingTransactionsGauge(TransactionRepository transactions) {
        return registry -> Gauge.builder("transactions.pending",
                        transactions, repo -> repo.countByStatus(TransactionStatus.PENDING_RATE))
                .description("Transactions waiting for an exchange rate")
                .register(registry);
    }
}