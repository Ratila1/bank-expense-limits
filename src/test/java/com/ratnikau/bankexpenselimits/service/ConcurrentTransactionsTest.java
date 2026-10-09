package com.ratnikau.bankexpenselimits.service;

import com.ratnikau.bankexpenselimits.AbstractIntegrationTest;
import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.domain.Transaction;
import com.ratnikau.bankexpenselimits.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.ratnikau.bankexpenselimits.support.TestData.ACCOUNT;
import static com.ratnikau.bankexpenselimits.support.TestData.usd;
import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentTransactionsTest extends AbstractIntegrationTest {

    private static final String DATETIME = "2022-01-15T10:00:00Z";

    @Autowired
    private TransactionService transactionService;

    // Проверяет, что ровно десять параллельных транзакций укладываются в лимит
    @Test
    void twentyParallelTransactions_exactlyTenFitIntoLimit() throws Exception {
        List<TransactionRequest> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            requests.add(usd(ACCOUNT, Category.PRODUCT, "100.00", DATETIME));
        }

        runAllAtOnce(requests);

        List<Transaction> saved = transactionRepository.findAll(Sort.by("id"));
        assertThat(saved).hasSize(20);
        assertFirstTenFitAndRestExceeded(saved);
    }

    // Проверяет независимую обработку транзакций разных категорий
    @Test
    void parallelTransactionsOfDifferentCategories_doNotAffectEachOther() throws Exception {
        List<TransactionRequest> requests = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            requests.add(usd(ACCOUNT, Category.PRODUCT, "100.00", DATETIME));
            requests.add(usd(ACCOUNT, Category.SERVICE, "100.00", DATETIME));
        }

        runAllAtOnce(requests);

        List<Transaction> saved = transactionRepository.findAll(Sort.by("id"));
        assertThat(saved).hasSize(30);
        assertFirstTenFitAndRestExceeded(onlyCategory(saved, Category.PRODUCT));
        assertFirstTenFitAndRestExceeded(onlyCategory(saved, Category.SERVICE));
    }

    // Все запросы стартуют по сигналу одновременно
    private void runAllAtOnce(List<TransactionRequest> requests) throws Exception {
        var startSignal = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Transaction>> futures = requests.stream()
                    .map(request -> executor.submit(() -> {
                        startSignal.await();
                        return transactionService.accept(request);
                    }))
                    .toList();
            startSignal.countDown();
            for (Future<Transaction> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }
    }

    private void assertFirstTenFitAndRestExceeded(List<Transaction> orderedById) {
        assertThat(orderedById.subList(0, 10)).allMatch(tx -> !tx.isLimitExceeded());
        assertThat(orderedById.subList(10, orderedById.size())).allMatch(Transaction::isLimitExceeded);
    }

    private static List<Transaction> onlyCategory(List<Transaction> all, Category category) {
        return all.stream().filter(tx -> tx.getExpenseCategory() == category).toList();
    }
}