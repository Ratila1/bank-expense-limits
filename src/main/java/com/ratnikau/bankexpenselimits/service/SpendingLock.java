package com.ratnikau.bankexpenselimits.service;

import com.ratnikau.bankexpenselimits.domain.Category;
import com.ratnikau.bankexpenselimits.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SpendingLock {

    private final TransactionRepository transactions;

    // Блокировка по паре (счёт, категория) до конца текущей DB-транзакции
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String account, Category category) {
        transactions.lockByKey(account + ":" + category.getValue());
    }
}