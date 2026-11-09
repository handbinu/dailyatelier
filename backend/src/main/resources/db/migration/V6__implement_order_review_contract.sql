DELETE FROM review;

ALTER TABLE review
    DROP FOREIGN KEY fk_review_user,
    DROP FOREIGN KEY fk_review_art;

ALTER TABLE review
    DROP COLUMN img_path,
    ADD COLUMN order_id BIGINT NOT NULL AFTER art_id,
    ADD COLUMN star INT NOT NULL AFTER order_id,
    ADD COLUMN created_at DATETIME(6) NOT NULL AFTER content,
    ADD COLUMN updated_at DATETIME(6) NOT NULL AFTER created_at,
    MODIFY COLUMN user_id VARCHAR(45) NOT NULL,
    MODIFY COLUMN art_id BIGINT NOT NULL,
    ADD CONSTRAINT uq_review_order UNIQUE (order_id),
    ADD CONSTRAINT fk_review_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    ADD CONSTRAINT fk_review_art
        FOREIGN KEY (art_id) REFERENCES art (art_id),
    ADD CONSTRAINT fk_review_order
        FOREIGN KEY (order_id) REFERENCES orders (order_id),
    ADD CONSTRAINT chk_review_star CHECK (star BETWEEN 1 AND 10),
    ADD INDEX idx_review_user_created (user_id, created_at, review_id),
    ADD INDEX idx_review_art_created (art_id, created_at, review_id);
