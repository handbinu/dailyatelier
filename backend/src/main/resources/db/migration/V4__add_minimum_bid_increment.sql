ALTER TABLE art
    ADD COLUMN minimum_bid_increment INT NOT NULL DEFAULT 1000 AFTER current_price,
    ADD CONSTRAINT chk_art_minimum_bid_increment
        CHECK (
            minimum_bid_increment BETWEEN 100 AND 10000000
            AND MOD(minimum_bid_increment, 100) = 0
        );
