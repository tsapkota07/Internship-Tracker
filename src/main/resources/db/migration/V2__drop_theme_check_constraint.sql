-- V2: Drop the auto-generated CHECK constraint on user_preferences.theme
-- Hibernate generated this with only LIGHT/DARK when the table was first created.
-- The application-level enum is the constraint — no DB check needed.
ALTER TABLE user_preferences DROP CONSTRAINT IF EXISTS user_preferences_theme_check;
