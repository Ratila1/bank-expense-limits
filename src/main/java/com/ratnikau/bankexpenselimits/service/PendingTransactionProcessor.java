package com.ratnikau.bankexpenselimits.service;


import com.ratnikau.bankexpenselimits.config.AppProperties;
import com.ratnikau.bankexpenselimits.domain.TransactionStatus;
import com.ratnikau.bankexpenselimits.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class PendingTransactionProcessor {

    private static final int BATCH_SIZE = 50;

    private final TransactionRepository transactions;
    private final TransactionService transactionService;
    private final ExchangeRateService rateService;
    private final AppProperties props;

    // Раз в минуту подбирать транзакции PENDING_RATE и пытаться досчитать их, когда появится курс
    @Scheduled(fixedDelayString = "${app.pending-retry-delay:PT1M}")
    public void process() {
        var pending = transactions.findByStatusOrderByOccurredAtAsc(
                TransactionStatus.PENDING_RATE, Limit.of(BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retrying {} transactions waiting for exchange rate", pending.size());
        for (var tx : pending) {
            try {
                var date = tx.getOccurredAt().atZone(props.zone()).toLocalDate();
                rateService.findUnitsPerUsd(tx.getCurrency(), date)
                        .ifPresent(rate -> transactionService.completePending(tx.getId(), rate));
            } catch (Exception e) {
                log.error("Failed to complete pending transaction id={}", tx.getId(), e);
            }
        }
    }
}