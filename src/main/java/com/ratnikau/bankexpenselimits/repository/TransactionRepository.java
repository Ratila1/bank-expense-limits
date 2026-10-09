package com.ratnikau.bankexpenselimits.repository;

import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.domain.Transaction;
import com.ratnikau.bankexpenselimits.domain.TransactionStatus;

import org.hibernate.query.spi.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Сумма расходов в USD за период [from, to) по счёту и категории
    @Query("""
            select coalesce(sum(t.sumUsd), 0)
            from Transaction t
            where t.accountFrom = :account
              and t.expenseCategory = :category
              and t.sumUsd is not null
              and t.occurredAt >= :from
              and t.occurredAt < :to
            """)
    BigDecimal sumUsdForPeriod(@Param("account") String account,
                               @Param("category") Category category,
                               @Param("from") Instant from,
                               @Param("to") Instant to);

    // Транзакции, превысившие лимит, вместе с лимитом (без N+1)
    @Query("""
            select t from Transaction t
            join fetch t.limit
            where t.accountFrom = :account
              and t.limitExceeded = true
            order by t.occurredAt
            """)
    List<Transaction> findExceeded(@Param("account") String account);

    // Блокировка до конца DB-транзакции: параллельные запросы по паре (счёт, категория) встают в очередь
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(hashtext(:key))) t",
            nativeQuery = true)
    long lockByKey(@Param("key") String key);

    List<Transaction> findByStatusOrderByOccurredAtAsc(TransactionStatus status, org.springframework.data.domain.Limit limit);

    long countByStatus(TransactionStatus status);
}