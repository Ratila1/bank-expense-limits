package com.ratnikau.bankexpenselimits.service;

import com.ratnikau.bankexpenselimits.config.AppProperties;
import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.domain.ExpenseLimit;
import com.ratnikau.bankexpenselimits.repository.ExpenseLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LimitServiceTest {

    private static final String ACCOUNT = "0000000123";
    private static final Instant NOW = Instant.parse("2022-01-10T00:00:00.123456789Z");

    @Mock
    private ExpenseLimitRepository limits;
    @Mock
    private SpendingLock spendingLock;

    private LimitService service;

    @BeforeEach
    void setUp() {
        var props = new AppProperties(ZoneOffset.UTC, new BigDecimal("1000.00"));
        service = new LimitService(limits, spendingLock, Clock.fixed(NOW, ZoneOffset.UTC), props);
    }

    // Проверяет использование времени из Clock при установке лимита
    @Test
    void setLimit_takesDateFromClock_notFromCaller() {
        when(limits.save(any(ExpenseLimit.class))).thenAnswer(call -> call.getArgument(0));

        ExpenseLimit limit = service.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("1500.00"));

        assertThat(limit.getSetAt()).isEqualTo(Instant.parse("2022-01-10T00:00:00.123456Z"));
    }

    // Проверяет сохранение счета, категории и суммы лимита
    @Test
    void setLimit_storesAccountCategoryAndAmountWithTwoDecimals() {
        when(limits.save(any(ExpenseLimit.class))).thenAnswer(call -> call.getArgument(0));

        ExpenseLimit limit = service.setLimit(ACCOUNT, Category.SERVICE, new BigDecimal("1500"));

        assertThat(limit.getAccount()).isEqualTo(ACCOUNT);
        assertThat(limit.getCategory()).isEqualTo(Category.SERVICE);
        assertThat(limit.getAmountUsd()).isEqualTo(new BigDecimal("1500.00"));
    }

    // Проверяет установку блокировки перед сохранением лимита
    @Test
    void setLimit_takesLockBeforeSaving() {
        when(limits.save(any(ExpenseLimit.class))).thenAnswer(call -> call.getArgument(0));

        service.setLimit(ACCOUNT, Category.PRODUCT, new BigDecimal("1500.00"));

        InOrder order = inOrder(spendingLock, limits);
        order.verify(spendingLock).lock(ACCOUNT, Category.PRODUCT);
        order.verify(limits).save(any(ExpenseLimit.class));
    }

    // Проверяет возврат существующего лимита без создания лимита по умолчанию
    @Test
    void effectiveLimit_returnsExistingLimit_withoutCreatingDefault() {
        ExpenseLimit existing = new ExpenseLimit();
        Instant at = Instant.parse("2022-01-05T00:00:00Z");
        when(limits.findEffective(ACCOUNT, "product", at)).thenReturn(Optional.of(existing));

        ExpenseLimit result = service.effectiveLimit(ACCOUNT, Category.PRODUCT, at);

        assertThat(result).isSameAs(existing);
        verify(limits, never()).save(any());
    }

    // Проверяет создание лимита по умолчанию при его отсутствии
    @Test
    void effectiveLimit_createsDefaultLimit_whenNoneExists() {
        Instant at = Instant.parse("2022-01-05T00:00:00Z");
        when(limits.findEffective(ACCOUNT, "product", at)).thenReturn(Optional.empty());
        when(limits.save(any(ExpenseLimit.class))).thenAnswer(call -> call.getArgument(0));

        ExpenseLimit result = service.effectiveLimit(ACCOUNT, Category.PRODUCT, at);

        ArgumentCaptor<ExpenseLimit> saved = ArgumentCaptor.forClass(ExpenseLimit.class);
        verify(limits).save(saved.capture());
        assertThat(result.getAmountUsd()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(saved.getValue().getSetAt()).isEqualTo(Instant.EPOCH);
        assertThat(saved.getValue().getAccount()).isEqualTo(ACCOUNT);
        assertThat(saved.getValue().getCategory()).isEqualTo(Category.PRODUCT);
    }

    // Проверяет получение всех лимитов счета из репозитория
    @Test
    void findAll_returnsLimitsFromRepository() {
        List<ExpenseLimit> stored = List.of(new ExpenseLimit(), new ExpenseLimit());
        when(limits.findByAccountOrderBySetAtDescIdDesc(ACCOUNT)).thenReturn(stored);

        assertThat(service.findAll(ACCOUNT)).isEqualTo(stored);
    }
}