package com.ratnikau.bankexpenselimits.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_from", nullable = false, length = 10)
    private String accountFrom;

    @Column(name = "account_to", nullable = false, length = 10)
    private String accountTo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "sum", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_category", nullable = false, length = 10)
    private Category expenseCategory;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "sum_usd", precision = 19, scale = 2)
    private BigDecimal sumUsd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "limit_exceeded", nullable = false)
    private boolean limitExceeded;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "limit_id")
    private ExpenseLimit limit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}