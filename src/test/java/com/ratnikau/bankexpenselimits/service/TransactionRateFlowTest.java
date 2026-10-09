package com.ratnikau.bankexpenselimits.service;

import com.ratnikau.bankexpenselimits.AbstractIntegrationTest;
import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.domain.ExchangeRate;
import com.ratnikau.bankexpenselimits.domain.RateKind;
import com.ratnikau.bankexpenselimits.domain.Transaction;
import com.ratnikau.bankexpenselimits.domain.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.ratnikau.bankexpenselimits.support.TestData.ACCOUNT;
import static com.ratnikau.bankexpenselimits.support.TestData.request;
import static com.ratnikau.bankexpenselimits.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;

class TransactionRateFlowTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private PendingTransactionProcessor pendingProcessor;

    // Проверяет однократное получение курса и последующее чтение из базы
    @Test
    void rate_isFetchedFromProviderOnce_andThenTakenFromDb() {
        givenProviderReturns("KZT", "2021-12-31", "480");

        Transaction first = kzt("48000.00", "2022-01-03T10:00:00+06:00");
        Transaction second = kzt("96000.00", "2022-01-03T12:00:00+06:00");

        assertThat(first.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(first.getSumUsd()).isEqualTo(new BigDecimal("100.00"));
        assertThat(second.getSumUsd()).isEqualTo(new BigDecimal("200.00"));
        verifyProviderCalls(1);
    }

    // Проверяет сохранение курса с датой транзакции и типом предыдущего закрытия
    @Test
    void rate_isStoredWithRequestedDateAndPreviousCloseKind_whenCandleIsFromEarlierDay() {
        givenProviderReturns("KZT", "2021-12-31", "480");

        kzt("48000.00", "2022-01-03T10:00:00+06:00");

        List<ExchangeRate> stored = rateRepository.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getRateDate()).isEqualTo(LocalDate.of(2022, 1, 3));
        assertThat(stored.get(0).getRateKind()).isEqualTo(RateKind.PREVIOUS_CLOSE);
        assertThat(stored.get(0).getRate()).isEqualByComparingTo("480");
    }

    // Проверяет сохранение курса с типом закрытия при наличии свечи за этот день
    @Test
    void rate_isStoredAsClose_whenCandleExistsForTheDay() {
        givenProviderReturns("KZT", "2022-01-03", "480.5");

        kzt("48000.00", "2022-01-03T10:00:00+06:00");

        assertThat(rateRepository.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getRateKind()).isEqualTo(RateKind.CLOSE));
    }

    // Проверяет использование последнего известного курса при недоступности провайдера
    @Test
    void lastKnownRate_isUsed_whenProviderKeepsFailing() {
        givenRateInDb("KZT", "2022-01-03", "450");
        givenProviderIsDown();

        Transaction tx = kzt("45000.00", "2022-01-05T10:00:00+06:00");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(tx.getSumUsd()).isEqualTo(new BigDecimal("100.00"));
        verifyProviderCalls(3);            // три попытки, затем запасной курс
        assertThat(rateRepository.count()).isEqualTo(1);
    }

    // Проверяет сохранение транзакции в статусе ожидания курса
    @Test
    void transaction_isSavedAsPending_whenThereIsNoRateAtAll() {
        Transaction tx = kzt("48000.00", "2022-01-03T10:00:00+06:00");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING_RATE);
        assertThat(tx.getSumUsd()).isNull();
        assertThat(tx.isLimitExceeded()).isFalse();
        assertThat(tx.getLimit()).isNull();
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    // Проверяет, что ожидающая транзакция не учитывается в месячной сумме
    @Test
    void pendingTransaction_isNotCountedInMonthlyTotal() {
        kzt("480000.00", "2022-01-03T10:00:00+06:00");       // PENDING

        Transaction tx = transactionService.accept(
                usd(ACCOUNT, Category.PRODUCT, "1000.00", "2022-01-04T10:00:00+06:00"));

        assertThat(tx.isLimitExceeded()).isFalse();
    }

    // Проверяет расчёт суммы в USD и флага превышения для ожидающей транзакции
    @Test
    void completePending_calculatesUsdAndFlag() {
        Transaction pending = kzt("600000.00", "2022-01-03T10:00:00+06:00");

        transactionService.completePending(pending.getId(), new BigDecimal("480"));

        Transaction done = transactionRepository.findById(pending.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(done.getSumUsd()).isEqualTo(new BigDecimal("1250.00"));
        assertThat(done.isLimitExceeded()).as("1250 > 1000 по умолчанию").isTrue();
        assertThat(done.getLimit()).isNotNull();
    }

    // Проверяет отсутствие изменений для уже обработанной транзакции
    @Test
    void completePending_doesNothing_forAlreadyProcessedTransaction() {
        givenRateInDb("KZT", "2022-01-03", "480");
        Transaction processed = kzt("48000.00", "2022-01-03T10:00:00+06:00");

        transactionService.completePending(processed.getId(), new BigDecimal("1"));

        Transaction reloaded = transactionRepository.findById(processed.getId()).orElseThrow();
        assertThat(reloaded.getSumUsd()).isEqualTo(new BigDecimal("100.00"));
    }

    // Проверяет обработку ожидающей транзакции после восстановления провайдера
    @Test
    void scheduler_completesPendingTransaction_whenProviderRecovers() {
        Transaction pending = kzt("48000.00", "2022-01-03T10:00:00+06:00");
        givenProviderReturns("KZT", "2021-12-31", "480");

        pendingProcessor.process();

        Transaction done = transactionRepository.findById(pending.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(done.getSumUsd()).isEqualTo(new BigDecimal("100.00"));
    }

    // Проверяет сохранение статуса ожидания при отсутствии курса
    @Test
    void scheduler_leavesTransactionPending_whenRateIsStillMissing() {
        Transaction pending = kzt("48000.00", "2022-01-03T10:00:00+06:00");

        pendingProcessor.process();

        Transaction reloaded = transactionRepository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.PENDING_RATE);
    }

    private Transaction kzt(String sum, String datetime) {
        return transactionService.accept(request(ACCOUNT, Category.PRODUCT, "KZT", sum, datetime));
    }
}