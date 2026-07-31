ALTER TABLE art
    ADD COLUMN active_point_hold_id BIGINT NULL,
    ADD CONSTRAINT fk_art_active_point_hold
        FOREIGN KEY (active_point_hold_id) REFERENCES point_hold (hold_id),
    ADD CONSTRAINT uq_art_active_point_hold
        UNIQUE (active_point_hold_id);
