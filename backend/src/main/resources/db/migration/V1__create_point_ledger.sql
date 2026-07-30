ALTER TABLE users
    MODIFY COLUMN reserve INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS point_account (
    user_id VARCHAR(45) NOT NULL,
    available_balance BIGINT NOT NULL,
    held_balance BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_point_account_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT chk_point_account_available
        CHECK (available_balance >= 0),
    CONSTRAINT chk_point_account_held
        CHECK (held_balance >= 0)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS point_transaction (
    transaction_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(45) NOT NULL,
    type VARCHAR(30) NOT NULL,
    amount BIGINT NOT NULL,
    available_delta BIGINT NOT NULL,
    held_delta BIGINT NOT NULL,
    available_balance_after BIGINT NOT NULL,
    held_balance_after BIGINT NOT NULL,
    reference_type VARCHAR(30) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    reversal_of_transaction_id BIGINT NULL,
    reason_code VARCHAR(50) NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (transaction_id),
    CONSTRAINT uq_point_transaction_idempotency
        UNIQUE (idempotency_key),
    CONSTRAINT uq_point_transaction_reversal_type
        UNIQUE (reversal_of_transaction_id, type),
    CONSTRAINT fk_point_transaction_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_point_transaction_reversal
        FOREIGN KEY (reversal_of_transaction_id)
        REFERENCES point_transaction (transaction_id),
    CONSTRAINT chk_point_transaction_amount
        CHECK (amount > 0),
    CONSTRAINT chk_point_transaction_available_after
        CHECK (available_balance_after >= 0),
    CONSTRAINT chk_point_transaction_held_after
        CHECK (held_balance_after >= 0),
    INDEX idx_point_transaction_user_created
        (user_id, created_at, transaction_id),
    INDEX idx_point_transaction_reference
        (reference_type, reference_id, type)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS point_hold (
    hold_id BIGINT NOT NULL AUTO_INCREMENT,
    art_id BIGINT NOT NULL,
    user_id VARCHAR(45) NOT NULL,
    latest_bid_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    released_at DATETIME(6) NULL,
    committed_at DATETIME(6) NULL,
    release_reason VARCHAR(30) NULL,
    commit_order_id BIGINT NULL,
    PRIMARY KEY (hold_id),
    CONSTRAINT fk_point_hold_art
        FOREIGN KEY (art_id) REFERENCES art (art_id),
    CONSTRAINT fk_point_hold_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_point_hold_latest_bid
        FOREIGN KEY (latest_bid_id) REFERENCES bid (bid_id),
    CONSTRAINT fk_point_hold_commit_order
        FOREIGN KEY (commit_order_id) REFERENCES orders (order_id),
    CONSTRAINT chk_point_hold_amount
        CHECK (amount > 0),
    INDEX idx_point_hold_art_created (art_id, created_at),
    INDEX idx_point_hold_user_status_created (user_id, status, created_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS point_charge (
    charge_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(45) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    merchant_order_id VARCHAR(100) NOT NULL,
    pg_order_id VARCHAR(100) NULL,
    requested_amount BIGINT NOT NULL,
    paid_amount BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    failure_code VARCHAR(50) NULL,
    failure_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    refunded_at DATETIME(6) NULL,
    charge_transaction_id BIGINT NULL,
    refund_transaction_id BIGINT NULL,
    PRIMARY KEY (charge_id),
    CONSTRAINT uq_point_charge_merchant_order
        UNIQUE (merchant_order_id),
    CONSTRAINT uq_point_charge_provider_pg_order
        UNIQUE (provider, pg_order_id),
    CONSTRAINT uq_point_charge_user_idempotency
        UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_point_charge_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_point_charge_transaction
        FOREIGN KEY (charge_transaction_id)
        REFERENCES point_transaction (transaction_id),
    CONSTRAINT fk_point_charge_refund_transaction
        FOREIGN KEY (refund_transaction_id)
        REFERENCES point_transaction (transaction_id),
    CONSTRAINT chk_point_charge_requested_amount
        CHECK (requested_amount > 0),
    CONSTRAINT chk_point_charge_paid_amount
        CHECK (paid_amount >= 0)
) ENGINE = InnoDB;

INSERT INTO point_account (
    user_id,
    available_balance,
    held_balance,
    version,
    created_at,
    updated_at
)
SELECT
    u.user_id,
    u.reserve,
    0,
    0,
    COALESCE(u.join_date, CURRENT_TIMESTAMP(6)),
    CURRENT_TIMESTAMP(6)
FROM users u
WHERE NOT EXISTS (
    SELECT 1
    FROM point_account account
    WHERE account.user_id = u.user_id
);

INSERT INTO point_transaction (
    user_id,
    type,
    amount,
    available_delta,
    held_delta,
    available_balance_after,
    held_balance_after,
    reference_type,
    reference_id,
    idempotency_key,
    reversal_of_transaction_id,
    reason_code,
    description,
    created_at
)
SELECT
    u.user_id,
    'OPENING_BALANCE',
    u.reserve,
    u.reserve,
    0,
    u.reserve,
    0,
    'USER',
    u.user_id,
    CONCAT('opening-balance:', u.user_id),
    NULL,
    'LEGACY_RESERVE_MIGRATION',
    '기존 보유 포인트 이관',
    CURRENT_TIMESTAMP(6)
FROM users u
WHERE u.reserve > 0
  AND NOT EXISTS (
      SELECT 1
      FROM point_transaction pt
      WHERE pt.idempotency_key =
            CONCAT('opening-balance:', u.user_id)
  );
