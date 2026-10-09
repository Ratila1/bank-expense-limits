package com.ratnikau.bankexpenselimits.repository;

import com.ratnikau.bankexpenselimits.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class AdvisoryLockTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    // Проверяет ожидание второй транзакции при одинаковом ключе блокировки
    @Test
    void lockByKey_makesSecondTransactionWait_untilFirstOneCommits() throws Exception {
        var template = new TransactionTemplate(transactionManager);
        var firstHoldsLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> template.executeWithoutResult(status -> {
                repository.lockByKey("0000000123:product");
                firstHoldsLock.countDown();
                await(releaseFirst);
            }));
            assertThat(firstHoldsLock.await(5, SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> template.executeWithoutResult(status ->
                    repository.lockByKey("0000000123:product")));
            Thread.sleep(500);
            assertThat(second.isDone()).as("вторая транзакция должна ждать блокировку").isFalse();

            releaseFirst.countDown();
            first.get(5, SECONDS);
            second.get(5, SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    // Проверяет отсутствие блокировки транзакции с другим ключом
    @Test
    void lockByKey_doesNotBlockTransactionWithAnotherKey() throws Exception {
        var template = new TransactionTemplate(transactionManager);
        var firstHoldsLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> template.executeWithoutResult(status -> {
                repository.lockByKey("0000000123:product");
                firstHoldsLock.countDown();
                await(releaseFirst);
            }));
            assertThat(firstHoldsLock.await(5, SECONDS)).isTrue();

            Future<?> other = executor.submit(() -> template.executeWithoutResult(status ->
                    repository.lockByKey("0000000123:service")));

            other.get(2, SECONDS);   
            releaseFirst.countDown();
            first.get(5, SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}