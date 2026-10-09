CREATE TABLE expense_limits (
    id          BIGSERIAL PRIMARY KEY,
    account     VARCHAR(10)    NOT NULL CHECK (account ~ '^[0-9]{10}$'),
    category    VARCHAR(10)    NOT NULL CHECK (category IN ('product', 'service')),
    amount_usd  NUMERIC(19, 2) NOT NULL CHECK (amount_usd > 0),
    set_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_limit_account_category_set_at UNIQUE (account, category, set_at)
);

CREATE INDEX idx_limits_lookup ON expense_limits (account, category, set_at DESC);

CREATE TABLE exchange_rates (
    id         BIGSERIAL PRIMARY KEY,
    currency   CHAR(3)        NOT NULL,
    rate_date  DATE           NOT NULL,
    rate       NUMERIC(19, 8) NOT NULL CHECK (rate > 0),
    rate_kind  VARCHAR(20)    NOT NULL CHECK (rate_kind IN ('CLOSE', 'PREVIOUS_CLOSE')),
    fetched_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_rate_currency_date UNIQUE (currency, rate_date)
);

CREATE TABLE transactions (
    id               BIGSERIAL PRIMARY KEY,
    account_from     VARCHAR(10)    NOT NULL CHECK (account_from ~ '^[0-9]{10}$'),
    account_to       VARCHAR(10)    NOT NULL CHECK (account_to ~ '^[0-9]{10}$'),
    currency         CHAR(3)        NOT NULL,
    sum              NUMERIC(19, 2) NOT NULL CHECK (sum > 0),
    expense_category VARCHAR(10)    NOT NULL CHECK (expense_category IN ('product', 'service')),
    occurred_at      TIMESTAMPTZ    NOT NULL,
    sum_usd          NUMERIC(19, 2),
    status           VARCHAR(20)    NOT NULL CHECK (status IN ('PROCESSED', 'PENDING_RATE')),
    limit_exceeded   BOOLEAN        NOT NULL DEFAULT FALSE,
    limit_id         BIGINT REFERENCES expense_limits (id),
    created_at       TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_tx_month_sum ON transactions (account_from, expense_category, occurred_at);
CREATE INDEX idx_tx_exceeded  ON transactions (account_from) WHERE limit_exceeded;