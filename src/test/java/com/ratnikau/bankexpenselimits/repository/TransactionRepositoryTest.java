package com.ratnikau.bankexpenselimits.repository;

import com.ratnikau.bankexpenselimits.AbstractIntegrationTest;
import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.domain.ExpenseLimit;
import com.ratnikau.bankexpenselimits.domain.Transaction;
import com.ratnikau.bankexpenselimits.domain.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRepositoryTest extends AbstractIntegrationTest {

    private static final String ACCOUNT = "0000000123";
    private static final Instant MONTH_START = Instant.parse("2022-01-01T00:00:00Z");
    private static final Instant MONTH_END = Instant.parse("2022-02-01T00:00:00Z");

    @Autowired
    private TransactionRepository repository;

    // Проверяет нулевую сумму при отсутствии транзакций
    @Test
    void sumUsdForPeriod_returnsZero_whenThereAreNoTransactions() {
        BigDecimal sum = repository.sumUsdForPeriod(ACCOUNT, Category.PRODUCT, MONTH_START, MONTH_END);

        assertThat(sum).isEqualByComparingTo("0");
    }

    // Проверяет суммирование транзакций за месяц
    @Test
    void sumUsdForPeriod_addsUpTransactionsOfTheMonth() {
        save(ACCOUNT, Category.PRODUCT, "2022-01-05T10:00:00Z", "100.00");
        save(ACCOUNT, Category.PRODUCT, "2022-01-20T10:00:00Z", "50.50");

        BigDecimal sum = repository.sumUsdForPeriod(ACCOUNT, Category.PRODUCT, MONTH_START, MONTH_END);

        assertThat(sum).isEqualByComparingTo("150.50");
    }

    // Проверяет включение транзакции в начальный момент периода
    @Test
    void sumUsdForPeriod_includesFirstMomentOfPeriod() {
        save(ACCOUNT, Category.PRODUCT, "2022-01-01T00:00:00Z", "100.00");

        BigDecimal sum = repository.sumUsdForPeriod(ACCOUNT, Category.PRODUCT, MONTH_START, MONTH_END);

        assertThat(sum).isEqualByComparingTo("100");
    }

    // Проверяет исключение транзакции в начальный момент следующего периода
    @Test
    void sumUsdForPeriod_excludesFirstMomentOfNextPeriod() {
        save(ACCOUNT, Category.PRODUCT, "2022-02-01T00:00:00Z", "100.00");

        BigDecimal sum = repository.sumUsdForPeriod(ACCOUNT, Category.PRODUCT, MONTH_START, MONTH_END);

        assertThat(sum).isEqualByComparingTo("0");
    }

    // Проверяет игнорирование транзакций другой категории
    @Test
    void sumUsdForPeriod_ignoresOtherCategory() {
        save(ACCOUNT, Category.SERVICE, "2022-01-05T10:00:00Z", "777.00");

        BigDecimal sum = repository.sumUsdForPeriod(ACCOUNT, Category.PRODUCT, MONTH_START, MONTH_END);

        assertThat(sum).isEqualByComparingTo("0");
    }

    // Проверяет игнорирование транзакций другого счета
    @Test
    void sumUsdForPeriod_ignoresOtherAccount() {
        save("9999999999", Category.PRODUCT, "2022-01-05T10:00:00Z", "888.00");

        BigDecimal sum = repository.sumUsdForPeriod(ACCOUNT, Category.PRODUCT, MONTH_START, MONTH_END);

        assertThat(sum).isEqualByComparingTo("0");
    }

    // Проверяет игнорирование транзакций без рассчитанного курса
    @Test
    void sumUsdForPeriod_ignoresTransactionsWaitingForRate() {
        save(ACCOUNT, Category.PRODUCT, "2022-01-05T10:00:00Z", null);

        BigDecimal sum = repository.sumUsdForPeriod(ACCOUNT, Category.PRODUCT, MONTH_START, MONTH_END);

        assertThat(sum).isEqualByComparingTo("0");
    }

    // Проверяет поиск превышенных транзакций в хронологическом порядке
    @Test
    void findExceeded_returnsOnlyExceededTransactionsOrderedByTime() {
        ExpenseLimit limit = saveLimit();
        Transaction later = save(ACCOUNT, Category.PRODUCT, "2022-01-13T00:00:00Z", "100.00");
        Transaction earlier = save(ACCOUNT, Category.PRODUCT, "2022-01-03T00:00:00Z", "600.00");
        save(ACCOUNT, Category.PRODUCT, "2022-01-02T00:00:00Z", "500.00");
        markExceeded(later, limit);
        markExceeded(earlier, limit);

        List<Transaction> result = repository.findExceeded(ACCOUNT);

        assertThat(result).extracting(Transaction::getId).containsExactly(earlier.getId(), later.getId());
    }

    // Проверяет загрузку лимита вместе с транзакцией
    @Test
    void findExceeded_loadsLimitTogetherWithTransaction() {
        ExpenseLimit limit = saveLimit();
        markExceeded(save(ACCOUNT, Category.PRODUCT, "2022-01-03T00:00:00Z", "600.00"), limit);

        List<Transaction> result = repository.findExceeded(ACCOUNT);

        assertThat(result.get(0).getLimit().getAmountUsd()).isEqualByComparingTo("1000");
    }

    // Проверяет игнорирование превышенных транзакций других счетов
    @Test
    void findExceeded_ignoresOtherAccounts() {
        ExpenseLimit limit = saveLimit();
        markExceeded(save("9999999999", Category.PRODUCT, "2022-01-03T00:00:00Z", "600.00"), limit);

        List<Transaction> result = repository.findExceeded(ACCOUNT);

        assertThat(result).isEmpty();
    }

    // Проверяет сортировку ожидающих транзакций и ограничение количества результатов
    @Test
    void findByStatusOrderByOccurredAtAsc_returnsOldestPendingFirstAndRespectsLimit() {
        Transaction third = save(ACCOUNT, Category.PRODUCT, "2022-01-03T00:00:00Z", null);
        Transaction first = save(ACCOUNT, Category.PRODUCT, "2022-01-01T00:00:00Z", null);
        Transaction second = save(ACCOUNT, Category.PRODUCT, "2022-01-02T00:00:00Z", null);
        save(ACCOUNT, Category.PRODUCT, "2022-01-04T00:00:00Z", "10.00");

        List<Transaction> result = repository.findByStatusOrderByOccurredAtAsc(
                TransactionStatus.PENDING_RATE, Limit.of(2));

        assertThat(result).extracting(Transaction::getId).containsExactly(first.getId(), second.getId());
        assertThat(third.getId()).isNotNull();
    }

    private Transaction save(String account, Category category, String occurredAt, String sumUsd) {
        var tx = new Transaction();
        tx.setAccountFrom(account);
        tx.setAccountTo("9999999999");
        tx.setCurrency("KZT");
        tx.setAmount(new BigDecimal("1000.00"));
        tx.setExpenseCategory(category);
        tx.setOccurredAt(Instant.parse(occurredAt));
        tx.setSumUsd(sumUsd == null ? null : new BigDecimal(sumUsd));
        tx.setStatus(sumUsd == null ? TransactionStatus.PENDING_RATE : TransactionStatus.PROCESSED);
        tx.setLimitExceeded(false);
        tx.setCreatedAt(Instant.parse("2022-01-01T00:00:00Z"));
        return repository.save(tx);
    }

    private void markExceeded(Transaction tx, ExpenseLimit limit) {
        tx.setLimitExceeded(true);
        tx.setLimit(limit);
        repository.save(tx);
    }

    private ExpenseLimit saveLimit() {
        var limit = new ExpenseLimit();
        limit.setAccount(ACCOUNT);
        limit.setCategory(Category.PRODUCT);
        limit.setAmountUsd(new BigDecimal("1000.00"));
        limit.setSetAt(Instant.parse("2022-01-01T00:00:00Z"));
        return limitRepository.save(limit);
    }
}