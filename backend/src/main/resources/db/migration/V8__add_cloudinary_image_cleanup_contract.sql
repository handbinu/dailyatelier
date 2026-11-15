ALTER TABLE art
    ADD COLUMN cloudinary_public_id VARCHAR(320)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL AFTER img_path,
    ADD CONSTRAINT uq_art_cloudinary_public_id UNIQUE (cloudinary_public_id);

ALTER TABLE users
    ADD COLUMN profile_image_public_id VARCHAR(320)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL AFTER profile_image_url,
    ADD CONSTRAINT uq_users_profile_image_public_id UNIQUE (profile_image_public_id);

CREATE TABLE cloudinary_cleanup (
    cleanup_id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(320)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    active_public_id VARCHAR(320)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('PENDING', 'PROCESSING') THEN public_id
                ELSE NULL
            END
        ) STORED,
    PRIMARY KEY (cleanup_id),
    CONSTRAINT uq_cloudinary_cleanup_active_public_id UNIQUE (active_public_id),
    CONSTRAINT chk_cloudinary_cleanup_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    CONSTRAINT chk_cloudinary_cleanup_attempt_count CHECK (attempt_count >= 0),
    INDEX idx_cloudinary_cleanup_pending (status, next_attempt_at, cleanup_id)
) ENGINE = InnoDB;
