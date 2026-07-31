SET @add_order_payment_method = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'orders'
          AND column_name = 'payment_method'
    ) = 0,
    'ALTER TABLE orders ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT ''INTERNAL_POINT''',
    'SELECT 1'
);
PREPARE add_order_payment_method_statement FROM @add_order_payment_method;
EXECUTE add_order_payment_method_statement;
DEALLOCATE PREPARE add_order_payment_method_statement;
