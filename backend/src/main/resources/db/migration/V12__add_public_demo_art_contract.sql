ALTER TABLE art
    ADD COLUMN public_demo_type VARCHAR(30) NULL,
    ADD COLUMN public_demo_creation_key VARCHAR(120) NULL,
    ADD CONSTRAINT uq_art_public_demo_creation_key UNIQUE (public_demo_creation_key);

CREATE INDEX idx_art_public_demo_active
    ON art (public_demo_type, art_status, closing_time, art_id);
