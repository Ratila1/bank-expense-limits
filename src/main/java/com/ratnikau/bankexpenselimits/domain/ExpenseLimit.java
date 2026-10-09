package com.ratnikau.bankexpenselimits.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "expense_limits")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String account;

    @Column(nullable = false, length = 10)
    private Category category;

    @Column(name = "amount_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountUsd;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;
}