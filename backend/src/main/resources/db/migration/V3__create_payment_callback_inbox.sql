CREATE TABLE IF NOT EXISTS payment_callback_event (
    callback_event_id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(20) NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    pg_order_id VARCHAR(100) NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    received_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (callback_event_id),
    CONSTRAINT uq_callback_provider_event
        UNIQUE (provider, provider_event_id),
    CONSTRAINT chk_callback_attempt_count
        CHECK (attempt_count >= 0),
    INDEX idx_callback_status_received
        (status, received_at, callback_event_id)
) ENGINE = InnoDB;
