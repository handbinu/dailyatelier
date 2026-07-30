SET @point_transaction_user_fk = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'point_transaction'
          AND column_name = 'user_id'
          AND referenced_table_name = 'users'
          AND referenced_column_name = 'user_id'
    ) = 0,
    'ALTER TABLE point_transaction ADD CONSTRAINT fk_point_transaction_user FOREIGN KEY (user_id) REFERENCES users (user_id)',
    'SELECT 1'
);
PREPARE point_transaction_user_statement FROM @point_transaction_user_fk;
EXECUTE point_transaction_user_statement;
DEALLOCATE PREPARE point_transaction_user_statement;

SET @point_transaction_reversal_fk = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'point_transaction'
          AND column_name = 'reversal_of_transaction_id'
          AND referenced_table_name = 'point_transaction'
          AND referenced_column_name = 'transaction_id'
    ) = 0,
    'ALTER TABLE point_transaction ADD CONSTRAINT fk_point_transaction_reversal FOREIGN KEY (reversal_of_transaction_id) REFERENCES point_transaction (transaction_id)',
    'SELECT 1'
);
PREPARE point_transaction_reversal_statement FROM @point_transaction_reversal_fk;
EXECUTE point_transaction_reversal_statement;
DEALLOCATE PREPARE point_transaction_reversal_statement;

SET @point_hold_order_fk = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'point_hold'
          AND column_name = 'commit_order_id'
          AND referenced_table_name = 'orders'
          AND referenced_column_name = 'order_id'
    ) = 0,
    'ALTER TABLE point_hold ADD CONSTRAINT fk_point_hold_commit_order FOREIGN KEY (commit_order_id) REFERENCES orders (order_id)',
    'SELECT 1'
);
PREPARE point_hold_order_statement FROM @point_hold_order_fk;
EXECUTE point_hold_order_statement;
DEALLOCATE PREPARE point_hold_order_statement;

SET @point_charge_user_fk = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'point_charge'
          AND column_name = 'user_id'
          AND referenced_table_name = 'users'
          AND referenced_column_name = 'user_id'
    ) = 0,
    'ALTER TABLE point_charge ADD CONSTRAINT fk_point_charge_user FOREIGN KEY (user_id) REFERENCES users (user_id)',
    'SELECT 1'
);
PREPARE point_charge_user_statement FROM @point_charge_user_fk;
EXECUTE point_charge_user_statement;
DEALLOCATE PREPARE point_charge_user_statement;

SET @point_charge_transaction_fk = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'point_charge'
          AND column_name = 'charge_transaction_id'
          AND referenced_table_name = 'point_transaction'
          AND referenced_column_name = 'transaction_id'
    ) = 0,
    'ALTER TABLE point_charge ADD CONSTRAINT fk_point_charge_transaction FOREIGN KEY (charge_transaction_id) REFERENCES point_transaction (transaction_id)',
    'SELECT 1'
);
PREPARE point_charge_transaction_statement FROM @point_charge_transaction_fk;
EXECUTE point_charge_transaction_statement;
DEALLOCATE PREPARE point_charge_transaction_statement;

SET @point_charge_refund_transaction_fk = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'point_charge'
          AND column_name = 'refund_transaction_id'
          AND referenced_table_name = 'point_transaction'
          AND referenced_column_name = 'transaction_id'
    ) = 0,
    'ALTER TABLE point_charge ADD CONSTRAINT fk_point_charge_refund_transaction FOREIGN KEY (refund_transaction_id) REFERENCES point_transaction (transaction_id)',
    'SELECT 1'
);
PREPARE point_charge_refund_statement FROM @point_charge_refund_transaction_fk;
EXECUTE point_charge_refund_statement;
DEALLOCATE PREPARE point_charge_refund_statement;
