CREATE TABLE users (
    user_id VARCHAR(45) NOT NULL,
    password VARCHAR(200) NOT NULL,
    name VARCHAR(50) NOT NULL,
    nickname VARCHAR(10) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    email VARCHAR(30) NOT NULL,
    join_date DATETIME(6) NOT NULL,
    user_status INT NOT NULL,
    reserve INT NULL DEFAULT 0,
    email_agree BIT NULL,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB;

CREATE TABLE artist (
    artist_code VARCHAR(36) NOT NULL,
    user_id VARCHAR(45) NOT NULL,
    artist_name VARCHAR(50) NULL,
    artist_intro VARCHAR(300) NULL,
    homepage VARCHAR(100) NULL,
    artist_sns VARCHAR(100) NULL,
    PRIMARY KEY (artist_code),
    CONSTRAINT uq_artist_user UNIQUE (user_id),
    CONSTRAINT fk_artist_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB;

CREATE TABLE art (
    art_id BIGINT NOT NULL AUTO_INCREMENT,
    artist_code VARCHAR(36) NULL,
    name VARCHAR(30) NOT NULL,
    descript VARCHAR(300) NULL,
    material VARCHAR(120) NULL,
    w_intro VARCHAR(500) NULL,
    start_price INT NOT NULL,
    current_price INT NOT NULL,
    bid_start_time DATETIME(6) NOT NULL,
    closing_time DATETIME(6) NOT NULL,
    img_path VARCHAR(255) NOT NULL,
    art_status INT NOT NULL,
    winning_bid_id BIGINT NULL,
    closed_at DATETIME(6) NULL,
    PRIMARY KEY (art_id),
    CONSTRAINT fk_art_artist
        FOREIGN KEY (artist_code) REFERENCES artist (artist_code),
    INDEX idx_art_close_candidates (art_status, closing_time, art_id)
) ENGINE = InnoDB;

CREATE TABLE bid (
    bid_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(45) NULL,
    art_id BIGINT NULL,
    bid_price INT NOT NULL,
    bid_time DATETIME(6) NOT NULL,
    PRIMARY KEY (bid_id),
    CONSTRAINT fk_bid_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_bid_art
        FOREIGN KEY (art_id) REFERENCES art (art_id)
) ENGINE = InnoDB;

ALTER TABLE art
    ADD CONSTRAINT fk_art_winning_bid
        FOREIGN KEY (winning_bid_id) REFERENCES bid (bid_id);

CREATE TABLE orders (
    order_id BIGINT NOT NULL AUTO_INCREMENT,
    art_id BIGINT NOT NULL,
    winning_bid_id BIGINT NOT NULL,
    buyer_id VARCHAR(45) NOT NULL,
    seller_id VARCHAR(45) NOT NULL,
    buyer_id_snapshot VARCHAR(45) NOT NULL,
    buyer_name_snapshot VARCHAR(50) NOT NULL,
    buyer_nickname_snapshot VARCHAR(10) NOT NULL,
    buyer_phone_snapshot VARCHAR(30) NOT NULL,
    seller_id_snapshot VARCHAR(45) NOT NULL,
    seller_name_snapshot VARCHAR(50) NOT NULL,
    seller_nickname_snapshot VARCHAR(10) NOT NULL,
    seller_artist_name_snapshot VARCHAR(50) NOT NULL,
    seller_phone_snapshot VARCHAR(30) NOT NULL,
    art_id_snapshot BIGINT NOT NULL,
    art_name_snapshot VARCHAR(30) NOT NULL,
    art_image_snapshot VARCHAR(500) NOT NULL,
    winning_bid_id_snapshot BIGINT NOT NULL,
    winning_price INT NOT NULL,
    recipient_name VARCHAR(50) NULL,
    recipient_phone VARCHAR(30) NULL,
    shipping_zip_code VARCHAR(5) NULL,
    shipping_address1 VARCHAR(100) NULL,
    shipping_address2 VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    payment_due_at DATETIME(6) NOT NULL,
    address_confirmed_at DATETIME(6) NULL,
    paid_at DATETIME(6) NULL,
    preparing_at DATETIME(6) NULL,
    shipped_at DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    confirmed_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    refunded_at DATETIME(6) NULL,
    cancel_reason VARCHAR(200) NULL,
    refund_reason VARCHAR(200) NULL,
    shipping_carrier VARCHAR(50) NULL,
    tracking_number VARCHAR(100) NULL,
    PRIMARY KEY (order_id),
    CONSTRAINT uq_orders_art UNIQUE (art_id),
    CONSTRAINT fk_orders_art
        FOREIGN KEY (art_id) REFERENCES art (art_id),
    CONSTRAINT fk_orders_winning_bid
        FOREIGN KEY (winning_bid_id) REFERENCES bid (bid_id),
    CONSTRAINT fk_orders_buyer
        FOREIGN KEY (buyer_id) REFERENCES users (user_id),
    CONSTRAINT fk_orders_seller
        FOREIGN KEY (seller_id) REFERENCES users (user_id),
    INDEX idx_orders_buyer_status_created (buyer_id, status, created_at),
    INDEX idx_orders_seller_status_created (seller_id, status, created_at),
    INDEX idx_orders_payment_expiration (status, payment_due_at, order_id)
) ENGINE = InnoDB;

CREATE TABLE address (
    user_id VARCHAR(45) NOT NULL,
    zip_code VARCHAR(5) NULL,
    user_address1 VARCHAR(100) NULL,
    user_address2 VARCHAR(100) NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_address_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB;

CREATE TABLE inquiry (
    inquiry_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(45) NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    answer VARCHAR(255) NULL,
    answered_at DATETIME(6) NULL,
    PRIMARY KEY (inquiry_id),
    CONSTRAINT fk_inquiry_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB;

CREATE TABLE likes (
    likes_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(45) NULL,
    art_id BIGINT NULL,
    PRIMARY KEY (likes_id),
    CONSTRAINT fk_likes_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_likes_art
        FOREIGN KEY (art_id) REFERENCES art (art_id)
) ENGINE = InnoDB;

CREATE TABLE review (
    review_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(45) NULL,
    art_id BIGINT NULL,
    content VARCHAR(300) NOT NULL,
    img_path VARCHAR(255) NULL,
    PRIMARY KEY (review_id),
    CONSTRAINT fk_review_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_review_art
        FOREIGN KEY (art_id) REFERENCES art (art_id)
) ENGINE = InnoDB;
