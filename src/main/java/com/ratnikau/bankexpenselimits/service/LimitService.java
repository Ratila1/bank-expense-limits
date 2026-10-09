package com.ratnikau.bankexpenselimits.service;


import com.ratnikau.bankexpenselimits.config.AppProperties;
import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.domain.ExpenseLimit;
import com.ratnikau.bankexpenselimits.repository.ExpenseLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;


@Service
@RequiredArgsConstructor
public class LimitService {

    private final ExpenseLimitRepository limits;
    private final SpendingLock spendingLock;
    private final Clock clock;
    private final AppProperties props;

    // Новый лимит: дату ставит сервис, существующие не меняются
    @Transactional
    public ExpenseLimit setLimit(String account, Category category, BigDecimal amountUsd) {
        spendingLock.lock(account, category);
        var limit = new ExpenseLimit();
        limit.setAccount(account);
        limit.setCategory(category);
        limit.setAmountUsd(amountUsd.setScale(2, RoundingMode.UNNECESSARY));
        limit.setSetAt(clock.instant().truncatedTo(ChronoUnit.MICROS)); // Postgres хранит микросекунды
        return limits.save(limit);
    }

    // Получить все лимиты счёта от новых к старым
    @Transactional(readOnly = true)
    public List<ExpenseLimit> findAll(String account) {
        return limits.findByAccountOrderBySetAtDescIdDesc(account);
    }

    // Вызывать только внутри транзакции и после SpendingLock.lock
    public ExpenseLimit effectiveLimit(String account, Category category, Instant at) {
        return limits.findEffective(account, category.getValue(), at)
                .orElseGet(() -> createDefault(account, category));
    }

    // Создать дефолтный лимит 1000 USD с датой 1970-01-01, если лимитов ещё не было
    private ExpenseLimit createDefault(String account, Category category) {
        var limit = new ExpenseLimit();
        limit.setAccount(account);
        limit.setCategory(category);
        limit.setAmountUsd(props.defaultLimitUsd());
        limit.setSetAt(Instant.EPOCH);
        return limits.save(limit);
    }
}