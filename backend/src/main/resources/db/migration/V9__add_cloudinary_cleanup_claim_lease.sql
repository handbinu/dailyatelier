ALTER TABLE cloudinary_cleanup
    ADD COLUMN processing_token VARCHAR(36) NULL AFTER status,
    ADD COLUMN processing_deadline DATETIME(6) NULL AFTER processing_token,
    ADD CONSTRAINT chk_cloudinary_cleanup_claim
        CHECK (
            (status = 'PROCESSING'
                AND processing_token IS NOT NULL
                AND processing_deadline IS NOT NULL)
            OR
            (status <> 'PROCESSING'
                AND processing_token IS NULL
                AND processing_deadline IS NULL)
        ),
    ADD INDEX idx_cloudinary_cleanup_processing (
        status, processing_deadline, cleanup_id
    );
