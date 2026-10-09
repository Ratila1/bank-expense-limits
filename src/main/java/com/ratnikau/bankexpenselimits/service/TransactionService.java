package com.ratnikau.bankexpenselimits.service;


import com.ratnikau.bankexpenselimits.config.AppProperties;
import com.ratnikau.bankexpenselimits.domain.Transaction;
import com.ratnikau.bankexpenselimits.domain.TransactionStatus;
import com.ratnikau.bankexpenselimits.dto.TransactionRequest;
import com.ratnikau.bankexpenselimits.exception.BadRequestException;
import com.ratnikau.bankexpenselimits.mapper.TransactionMapper;
import com.ratnikau.bankexpenselimits.repository.TransactionRepository;
import com.ratnikau.bankexpenselimits.util.LimitRules;
import com.ratnikau.bankexpenselimits.util.MonthRange;
import com.ratnikau.bankexpenselimits.util.UsdConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactions;
    private final LimitService limitService;
    private final SpendingLock spendingLock;
    private final ExchangeRateService rateService;
    private final TransactionMapper transactionMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final AppProperties props;

    // Принять новую транзакцию: поиск курса, создание записи, проверка лимита или статус PENDING_RATE
    public Transaction accept(TransactionRequest request) {
        String currency = normalizeCurrency(request.currencyShortname());
        Instant occurredAt = request.datetime().toInstant();
        LocalDate rateDate = occurredAt.atZone(props.zone()).toLocalDate();

        // Курс ищем ДО транзакции БД: медленный вызов API не должен держать блокировку
        Optional<BigDecimal> rate = rateService.findUnitsPerUsd(currency, rateDate);

        return transactionTemplate.execute(status -> {
            var tx = newTransaction(request, currency);
            if (rate.isPresent()) {
                applyRate(tx, rate.get());
            } else {
                tx.setStatus(TransactionStatus.PENDING_RATE);
                log.warn("No exchange rate for {}, transaction saved as PENDING_RATE", currency);
            }
            return transactions.save(tx);
        });
    }

    // Досчёт транзакции, которая ждала курс. Вызывается планировщиком
    public void completePending(Long id, BigDecimal rate) {
        transactionTemplate.executeWithoutResult(status ->
                transactions.findById(id)
                        .filter(tx -> tx.getStatus() == TransactionStatus.PENDING_RATE)
                        .ifPresent(tx -> applyRate(tx, rate)));
    }

    // Получить список транзакций, превысивших лимит, с подгруженным лимитом
    @Transactional(readOnly = true)
    public List<Transaction> findExceeded(String account) {
        return transactions.findExceeded(account);
    }

    // Считает сумму в USD, находит действующий лимит и выставляет флаг. Только внутри DB-транзакции
    private void applyRate(Transaction tx, BigDecimal rate) {
        var category = tx.getExpenseCategory();
        spendingLock.lock(tx.getAccountFrom(), category);

        var limit = limitService.effectiveLimit(tx.getAccountFrom(), category, tx.getOccurredAt());
        var month = MonthRange.of(tx.getOccurredAt(), props.zone());
        var spentBefore = transactions.sumUsdForPeriod(tx.getAccountFrom(), category, month.from(), month.to());
        var sumUsd = UsdConverter.toUsd(tx.getAmount(), rate);

        tx.setSumUsd(sumUsd);
        tx.setStatus(TransactionStatus.PROCESSED);
        tx.setLimit(limit);
        tx.setLimitExceeded(LimitRules.isExceeded(spentBefore, sumUsd, limit.getAmountUsd()));

        log.info("Transaction processed: account={}, category={}, sumUsd={}, exceeded={}",
                tx.getAccountFrom(), category.getValue(), sumUsd, tx.isLimitExceeded());
    }

    // Создать новую сущность Transaction из DTO, с нормализованной валютой и суммой
    private Transaction newTransaction(TransactionRequest request, String currency) {
        var tx = transactionMapper.toEntity(request);
        tx.setCurrency(currency);                            
        tx.setAmount(request.sum().setScale(2, RoundingMode.UNNECESSARY));
        tx.setCreatedAt(clock.instant());
        return tx;
    }

    // Проверить код валюты через Currency.getInstance и вернуть в верхнем регистре
    private static String normalizeCurrency(String code) {
        String upper = code.toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(upper);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown currency code: " + code);
        }
        return upper;
    }
}