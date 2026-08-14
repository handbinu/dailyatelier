ALTER TABLE inquiry
    ADD COLUMN inquiry_type VARCHAR(20) NOT NULL DEFAULT 'OTHER' AFTER content,
    ADD COLUMN email_alert BIT NOT NULL DEFAULT b'1' AFTER inquiry_type,
    ADD COLUMN attachment_url VARCHAR(500) NULL AFTER email_alert,
    ADD COLUMN attachment_name VARCHAR(255) NULL AFTER attachment_url,
    ADD COLUMN attachment_resource_type VARCHAR(20) NULL AFTER attachment_name,
    MODIFY COLUMN answer VARCHAR(1000) NULL,
    ADD INDEX idx_inquiry_user_created (user_id, created_at, inquiry_id),
    ADD INDEX idx_inquiry_answered_created (answered_at, created_at, inquiry_id);
