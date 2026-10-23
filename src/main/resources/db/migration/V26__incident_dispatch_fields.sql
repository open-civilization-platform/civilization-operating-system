ALTER TABLE social_incidents
    ADD COLUMN assigned_eco_bots INTEGER DEFAULT 0,
    ADD COLUMN assigned_security_bots INTEGER DEFAULT 0,
    ADD COLUMN severity DOUBLE PRECISION DEFAULT 100.0;