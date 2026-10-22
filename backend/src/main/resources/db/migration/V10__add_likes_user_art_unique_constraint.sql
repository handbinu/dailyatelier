ALTER TABLE likes
    ADD CONSTRAINT uq_likes_user_art
        UNIQUE (user_id, art_id);
