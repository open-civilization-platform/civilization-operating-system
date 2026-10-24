ALTER TABLE technologies
    ADD COLUMN resource_bonus VARCHAR(255),
    ADD COLUMN bonus_amount DOUBLE PRECISION DEFAULT 0.0;