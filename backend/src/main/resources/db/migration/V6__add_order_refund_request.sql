ALTER TABLE orders
    ADD COLUMN refund_request_status VARCHAR(30) NULL,
    ADD COLUMN refund_request_reason VARCHAR(200) NULL,
    ADD COLUMN refund_requested_at DATETIME(6) NULL,
    ADD COLUMN refund_rejected_at DATETIME(6) NULL;
