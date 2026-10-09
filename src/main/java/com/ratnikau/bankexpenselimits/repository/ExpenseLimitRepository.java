package com.ratnikau.bankexpenselimits.repository;


import com.ratnikau.bankexpenselimits.domain.ExpenseLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.Instant;
import java.util.List;
import java.util.Optional;


public interface ExpenseLimitRepository extends JpaRepository<ExpenseLimit, Long> {

    // Найти последний лимит на момент at: JOIN с подзапросом MAX(set_at) <= at
    @Query(value = """
            SELECT l.*
            FROM expense_limits l
            JOIN (SELECT MAX(set_at) AS max_set_at
                  FROM expense_limits
                  WHERE account = :account
                    AND category = :category
                    AND set_at <= :at) m ON l.set_at = m.max_set_at
            WHERE l.account = :account
              AND l.category = :category
            """, nativeQuery = true)
    Optional<ExpenseLimit> findEffective(@Param("account") String account,
                                         @Param("category") String category,
                                         @Param("at") Instant at);

    // Получить все лимиты счёта, от новых к старым (по set_at DESC, id DESC)
    List<ExpenseLimit> findByAccountOrderBySetAtDescIdDesc(String account);
}