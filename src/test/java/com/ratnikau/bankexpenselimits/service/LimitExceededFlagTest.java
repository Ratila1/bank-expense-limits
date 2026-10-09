package com.ratnikau.bankexpenselimits.service;

import com.ratnikau.bankexpenselimits.AbstractIntegrationTest;
import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.domain.ExpenseLimit;
import com.ratnikau.bankexpenselimits.domain.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.ratnikau.bankexpenselimits.support.TestData.ACCOUNT;
import static com.ratnikau.bankexpenselimits.support.TestData.OTHER_ACCOUNT;
import static com.ratnikau.bankexpenselimits.support.TestData.request;
import static com.ratnikau.bankexpenselimits.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;

class LimitExceededFlagTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private LimitService limitService;


    // Проверяет флаги превышения по первому сценарию из задания
    @Test
    void case1_flagsFollowTheTaskTable() {
        List<Transaction> t = runCase1();

        assertThat(t.get(0).isLimitExceeded()).as("2.01: 500 из 1000").isFalse();
        assertThat(t.get(1).isLimitExceeded()).as("3.01: 500 + 600 > 1000").isTrue();
        assertThat(t.get(2).isLimitExceeded()).as("11.01: новый лимит 2000, 1100 + 100").isFalse();
        assertThat(t.get(3).isLimitExceeded()).as("12.01: 1200 + 700").isFalse();
        assertThat(t.get(4).isLimitExceeded()).as("13.01: остаток ровно 0").isFalse();
        assertThat(t.get(5).isLimitExceeded()).as("13.01: остаток -100").isTrue();
    }

    // Проверяет возврат превышенных транзакций с соответствующими лимитами
    @Test
    void case1_exceededTransactionsAreReportedTogetherWithTheirOwnLimit() {
        runCase1();

        List<Transaction> exceeded = transactionService.findExceeded(ACCOUNT);

        assertThat(exceeded).hasSize(2);
        ExpenseLimit firstLimit = exceeded.get(0).getLimit();
        assertThat(firstLimit.getAmountUsd()).isEqualByComparingTo("1000");
        assertThat(firstLimit.getSetAt()).isEqualTo(Instant.parse("2022-01-01T00:00:00Z"));
        ExpenseLimit secondLimit = exceeded.get(1).getLimit();
        assertThat(secondLimit.getAmountUsd()).isEqualByComparingTo("2000");
        assertThat(secondLimit.getSetAt()).isEqualTo(Instant.parse("2022-01-10T00:00:00Z"));
    }

    // Проверяет, что новый лимит не изменяет флаги прошлых транзакций
    @Test
    void case1_newLimitDoesNotChangeFlagsOfEarlierTransactions() {
        List<Transaction> t = runCase1();

        Transaction exceededBefore = transactionRepository.findById(t.get(1).getId()).orElseThrow();
        Transaction normalBefore = transactionRepository.findById(t.get(0).getId()).orElseThrow();

        assertThat(exceededBefore.isLimitExceeded()).isTrue();
        assertThat(normalBefore.isLimitExceeded()).isFalse();
    }

    // Проверяет флаги превышения по второму сценарию из задания
    @Test
    void case2_flagsFollowTheTaskTable() {
        clock.set("2022-02-01T00:00:00Z");
        limitService.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("1000.00"));
        Transaction a = spend("500.00", "2022-02-02T10:00:00+06:00");
        Transaction b = spend("100.00", "2022-02-03T10:00:00+06:00");
        clock.set("2022-02-10T00:00:00Z");
        limitService.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("400.00"));
        Transaction c = spend("100.00", "2022-02-11T10:00:00+06:00");
        Transaction d = spend("100.00", "2022-02-12T10:00:00+06:00");

        assertThat(a.isLimitExceeded()).isFalse();
        assertThat(b.isLimitExceeded()).isFalse();
        assertThat(c.isLimitExceeded()).as("лимит снизили до 400, уже потрачено 600").isTrue();
        assertThat(d.isLimitExceeded()).isTrue();
    }

    // Проверяет возврат транзакций с уменьшенным лимитом
    @Test
    void case2_exceededTransactionsAreReportedWithLoweredLimit() {
        clock.set("2022-02-01T00:00:00Z");
        limitService.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("1000.00"));
        spend("500.00", "2022-02-02T10:00:00+06:00");
        spend("100.00", "2022-02-03T10:00:00+06:00");
        clock.set("2022-02-10T00:00:00Z");
        limitService.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("400.00"));
        spend("100.00", "2022-02-11T10:00:00+06:00");
        spend("100.00", "2022-02-12T10:00:00+06:00");

        List<Transaction> exceeded = transactionService.findExceeded(ACCOUNT);

        assertThat(exceeded).hasSize(2);
        assertThat(exceeded).allSatisfy(tx ->
                assertThat(tx.getLimit().getAmountUsd()).isEqualByComparingTo("400"));
    }

    // Проверяет отсутствие превышения при остатке, равном нулю
    @Test
    void flag_isFalse_whenRemainderIsExactlyZero() {
        Transaction first = spend("400.00", "2022-01-05T10:00:00Z");
        Transaction second = spend("600.00", "2022-01-06T10:00:00Z");

        assertThat(first.isLimitExceeded()).isFalse();
        assertThat(second.isLimitExceeded()).isFalse();
    }

    // Проверяет превышение лимита на один цент
    @Test
    void flag_isTrue_whenLimitIsExceededByOneCent() {
        Transaction tx = spend("1000.01", "2022-01-05T10:00:00Z");

        assertThat(tx.isLimitExceeded()).isTrue();
    }

    // Проверяет использование лимита по умолчанию в размере 1000 USD
    @Test
    void flag_usesDefaultLimitOf1000_whenClientNeverSetOne() {
        Transaction first = spend("1000.00", "2022-01-05T10:00:00Z");
        Transaction second = spend("0.01", "2022-01-06T10:00:00Z");

        assertThat(first.isLimitExceeded()).isFalse();
        assertThat(second.isLimitExceeded()).isTrue();
    }

    // Проверяет отображение лимита по умолчанию с датой эпохи
    @Test
    void defaultLimit_appearsInLimitListWithEpochDate() {
        spend("10.00", "2022-01-05T10:00:00Z");

        List<ExpenseLimit> limits = limitService.findAll(ACCOUNT);

        assertThat(limits).hasSize(1);
        assertThat(limits.get(0).getAmountUsd()).isEqualByComparingTo("1000");
        assertThat(limits.get(0).getSetAt()).isEqualTo(Instant.EPOCH);
    }

    // Проверяет сброс месячной суммы в начале нового месяца
    @Test
    void monthlyTotal_startsFromZeroInTheNextMonth() {
        spend("900.00", "2022-01-31T23:59:59Z");

        Transaction february = spend("200.00", "2022-02-01T00:00:00Z");

        assertThat(february.isLimitExceeded()).isFalse();
    }

    // Проверяет общую месячную сумму транзакций
    @Test
    void monthlyTotal_isSharedInsideOneMonth() {
        spend("900.00", "2022-01-31T23:00:00Z");

        Transaction sameMonth = spend("200.00", "2022-01-31T23:59:59Z");

        assertThat(sameMonth.isLimitExceeded()).isTrue();
    }

    // Проверяет определение границы месяца по UTC
    @Test
    void monthBoundaryIsDefinedInUtc_notInTransactionOffset() {
        spend("900.00", "2022-01-31T10:00:00Z");

        Transaction tx = spend("200.00", "2022-02-01T03:00:00+06:00");

        assertThat(tx.isLimitExceeded()).isTrue();
    }

    // Проверяет применение лимита, действовавшего на дату транзакции
    @Test
    void transactionDatedBeforeNewLimit_isCheckedAgainstLimitThatWasActiveThen() {
        limitService.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("1000.00"));
        clock.set("2022-01-10T00:00:00Z");
        limitService.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("5000.00"));

        Transaction late = spend("1500.00", "2022-01-05T10:00:00Z");
        Transaction afterNewLimit = spend("100.00", "2022-01-11T10:00:00Z");

        assertThat(late.isLimitExceeded()).as("на 5 января действовал лимит 1000").isTrue();
        assertThat(afterNewLimit.isLimitExceeded()).as("с 10 января лимит 5000").isFalse();
    }

    // Проверяет независимый подсчёт сумм по категориям
    @Test
    void categories_haveIndependentTotals() {
        spend(ACCOUNT, Category.PRODUCT, "1000.00", "2022-01-05T10:00:00Z");

        Transaction service = spend(ACCOUNT, Category.SERVICE, "500.00", "2022-01-06T10:00:00Z");
        Transaction product = spend(ACCOUNT, Category.PRODUCT, "1.00", "2022-01-07T10:00:00Z");

        assertThat(service.isLimitExceeded()).isFalse();
        assertThat(product.isLimitExceeded()).isTrue();
    }

    // Проверяет независимый подсчёт сумм по счетам
    @Test
    void accounts_haveIndependentTotals() {
        spend(ACCOUNT, Category.PRODUCT, "1000.00", "2022-01-05T10:00:00Z");

        Transaction other = spend(OTHER_ACCOUNT, Category.PRODUCT, "500.00", "2022-01-06T10:00:00Z");

        assertThat(other.isLimitExceeded()).isFalse();
    }

    // Проверяет расчёт превышения по суммам в USD
    @Test
    void flag_countsSumInUsd_notInOriginalCurrency() {
        givenRateInDb("KZT", "2022-01-03", "480");

        Transaction first = transactionService.accept(
                request(ACCOUNT, Category.PRODUCT, "KZT", "240000.00", "2022-01-03T10:00:00+06:00"));
        Transaction second = transactionService.accept(
                request(ACCOUNT, Category.PRODUCT, "KZT", "300000.00", "2022-01-03T11:00:00+06:00"));

        assertThat(first.getSumUsd()).isEqualTo(new BigDecimal("500.00"));
        assertThat(first.isLimitExceeded()).isFalse();
        assertThat(second.getSumUsd()).isEqualTo(new BigDecimal("625.00"));
        assertThat(second.isLimitExceeded()).as("500 + 625 > 1000").isTrue();
        verifyProviderCalls(0);
    }


    // Шаги «случая 1» из задания, все суммы в USD. Возвращает шесть транзакций по порядку
    private List<Transaction> runCase1() {
        clock.set("2022-01-01T00:00:00Z");
        limitService.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("1000.00"));
        Transaction t1 = spend("500.00", "2022-01-02T10:00:00+06:00");
        Transaction t2 = spend("600.00", "2022-01-03T10:00:00+06:00");
        clock.set("2022-01-10T00:00:00Z");
        limitService.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("2000.00"));
        Transaction t3 = spend("100.00", "2022-01-11T10:00:00+06:00");
        Transaction t4 = spend("700.00", "2022-01-12T10:00:00+06:00");
        Transaction t5 = spend("100.00", "2022-01-13T10:00:00+06:00");
        Transaction t6 = spend("100.00", "2022-01-13T11:00:00+06:00");
        return List.of(t1, t2, t3, t4, t5, t6);
    }

    private Transaction spend(String sumUsd, String datetime) {
        return spend(ACCOUNT, Category.PRODUCT, sumUsd, datetime);
    }

    private Transaction spend(String account, Category category, String sumUsd, String datetime) {
        return transactionService.accept(usd(account, category, sumUsd, datetime));
    }
}