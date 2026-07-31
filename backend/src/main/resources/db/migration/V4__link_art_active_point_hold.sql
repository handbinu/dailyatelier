SET @add_active_hold_column = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'art'
          AND column_name = 'active_point_hold_id'
    ) = 0,
    'ALTER TABLE art ADD COLUMN active_point_hold_id BIGINT NULL',
    'SELECT 1'
);
PREPARE add_active_hold_column_statement FROM @add_active_hold_column;
EXECUTE add_active_hold_column_statement;
DEALLOCATE PREPARE add_active_hold_column_statement;

SET @add_active_hold_fk = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'art'
          AND column_name = 'active_point_hold_id'
          AND referenced_table_name = 'point_hold'
          AND referenced_column_name = 'hold_id'
    ) = 0,
    'ALTER TABLE art ADD CONSTRAINT fk_art_active_point_hold FOREIGN KEY (active_point_hold_id) REFERENCES point_hold (hold_id)',
    'SELECT 1'
);
PREPARE add_active_hold_fk_statement FROM @add_active_hold_fk;
EXECUTE add_active_hold_fk_statement;
DEALLOCATE PREPARE add_active_hold_fk_statement;

SET @add_active_hold_unique = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'art'
          AND column_name = 'active_point_hold_id'
          AND non_unique = 0
    ) = 0,
    'ALTER TABLE art ADD CONSTRAINT uq_art_active_point_hold UNIQUE (active_point_hold_id)',
    'SELECT 1'
);
PREPARE add_active_hold_unique_statement FROM @add_active_hold_unique;
EXECUTE add_active_hold_unique_statement;
DEALLOCATE PREPARE add_active_hold_unique_statement;
