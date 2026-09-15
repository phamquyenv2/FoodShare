ALTER TABLE food_posts
    ADD COLUMN hidden_by_admin BOOLEAN NOT NULL DEFAULT FALSE AFTER post_status;
