ALTER TABLE art
    ADD COLUMN format VARCHAR(20) NOT NULL AFTER material,
    ADD COLUMN category VARCHAR(30) NOT NULL AFTER format,
    ADD COLUMN created_at DATETIME(6) NOT NULL AFTER art_status,
    ADD INDEX idx_art_public_search
        (art_status, format, category, closing_time, art_id),
    ADD INDEX idx_art_created (created_at, art_id),
    ADD INDEX idx_art_current_price (current_price, art_id);
