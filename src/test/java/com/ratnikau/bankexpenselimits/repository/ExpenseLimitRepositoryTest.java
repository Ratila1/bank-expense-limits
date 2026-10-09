package com.ratnikau.bankexpenselimits.repository;

import com.ratnikau.bankexpenselimits.AbstractIntegrationTest;
import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.domain.ExpenseLimit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseLimitRepositoryTest extends AbstractIntegrationTest {

    private static final String ACCOUNT = "0000000123";

    @Autowired
    private ExpenseLimitRepository repository;

    // Проверяет пустой результат при отсутствии лимита
    @Test
    void findEffective_returnsEmpty_whenNoLimitExists() {
        Optional<ExpenseLimit> result = repository.findEffective(ACCOUNT, "product", at("2022-01-05T00:00:00Z"));

        assertThat(result).isEmpty();
    }

    // Проверяет получение первого лимита до установки второго
    @Test
    void findEffective_returnsFirstLimit_beforeSecondOneWasSet() {
        save(ACCOUNT, Category.PRODUCT, "1000.00", "2022-01-01T00:00:00Z");
        save(ACCOUNT, Category.PRODUCT, "2000.00", "2022-01-10T00:00:00Z");

        Optional<ExpenseLimit> result = repository.findEffective(ACCOUNT, "product", at("2022-01-05T00:00:00Z"));

        assertThat(result).hasValueSatisfying(l -> assertThat(l.getAmountUsd()).isEqualByComparingTo("1000"));
    }

    // Проверяет получение второго лимита после его установки
    @Test
    void findEffective_returnsSecondLimit_afterItWasSet() {
        save(ACCOUNT, Category.PRODUCT, "1000.00", "2022-01-01T00:00:00Z");
        save(ACCOUNT, Category.PRODUCT, "2000.00", "2022-01-10T00:00:00Z");

        Optional<ExpenseLimit> result = repository.findEffective(ACCOUNT, "product", at("2022-01-11T00:00:00Z"));

        assertThat(result).hasValueSatisfying(l -> assertThat(l.getAmountUsd()).isEqualByComparingTo("2000"));
    }

    // Проверяет включение лимита, установленного точно в заданный момент
    @Test
    void findEffective_includesLimitSetExactlyAtGivenMoment() {
        save(ACCOUNT, Category.PRODUCT, "1000.00", "2022-01-10T00:00:00Z");

        Optional<ExpenseLimit> result = repository.findEffective(ACCOUNT, "product", at("2022-01-10T00:00:00Z"));

        assertThat(result).isPresent();
    }

    // Проверяет игнорирование лимита, установленного позднее заданного момента
    @Test
    void findEffective_ignoresLimitSetLaterThanGivenMoment() {
        save(ACCOUNT, Category.PRODUCT, "1000.00", "2022-01-10T00:00:00Z");

        Optional<ExpenseLimit> result = repository.findEffective(ACCOUNT, "product", at("2022-01-09T23:59:59Z"));

        assertThat(result).isEmpty();
    }

    // Проверяет раздельный поиск лимитов по категориям
    @Test
    void findEffective_doesNotMixCategories() {
        save(ACCOUNT, Category.SERVICE, "300.00", "2022-01-01T00:00:00Z");

        Optional<ExpenseLimit> result = repository.findEffective(ACCOUNT, "product", at("2022-01-05T00:00:00Z"));

        assertThat(result).isEmpty();
    }

    // Проверяет раздельный поиск лимитов по счетам
    @Test
    void findEffective_doesNotMixAccounts() {
        save("9999999999", Category.PRODUCT, "5000.00", "2022-01-01T00:00:00Z");

        Optional<ExpenseLimit> result = repository.findEffective(ACCOUNT, "product", at("2022-01-05T00:00:00Z"));

        assertThat(result).isEmpty();
    }

    // Проверяет сортировку лимитов от нового к старому
    @Test
    void findByAccountOrderBySetAtDescIdDesc_returnsNewestLimitFirst() {
        save(ACCOUNT, Category.PRODUCT, "1000.00", "2022-01-01T00:00:00Z");
        save(ACCOUNT, Category.SERVICE, "300.00", "2022-01-10T00:00:00Z");
        save("9999999999", Category.PRODUCT, "5000.00", "2022-01-20T00:00:00Z");

        List<ExpenseLimit> result = repository.findByAccountOrderBySetAtDescIdDesc(ACCOUNT);

        assertThat(result).extracting(ExpenseLimit::getCategory)
                .containsExactly(Category.SERVICE, Category.PRODUCT);
    }

    // Проверяет запрет дублирования лимита для счета, категории и даты
    @Test
    void save_rejectsSecondLimitWithSameAccountCategoryAndDate() {
        save(ACCOUNT, Category.PRODUCT, "1000.00", "2022-01-01T00:00:00Z");

        assertThatThrownBy(() -> save(ACCOUNT, Category.PRODUCT, "2000.00", "2022-01-01T00:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Проверяет запрет сохранения нулевого лимита
    @Test
    void save_rejectsZeroAmount() {
        assertThatThrownBy(() -> save(ACCOUNT, Category.PRODUCT, "0.00", "2022-01-01T00:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ExpenseLimit save(String account, Category category, String amount, String setAt) {
        var limit = new ExpenseLimit();
        limit.setAccount(account);
        limit.setCategory(category);
        limit.setAmountUsd(new BigDecimal(amount));
        limit.setSetAt(at(setAt));
        return repository.saveAndFlush(limit);
    }

    private static Instant at(String value) {
        return Instant.parse(value);
    }
}