-- V1: Initial schema
-- This migration documents the schema as it existed at the time Flyway was introduced.
-- On the live database, Flyway will baseline at V1 (via baseline-on-migrate=true)
-- so this script will only run on fresh installs.

CREATE TABLE IF NOT EXISTS app_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(255) UNIQUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    pending_email  VARCHAR(255),
    role          VARCHAR(50)  NOT NULL DEFAULT 'USER',
    demo_user     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS internship_application (
    id           BIGSERIAL PRIMARY KEY,
    company      VARCHAR(255)  NOT NULL,
    role         VARCHAR(255)  NOT NULL,
    status       VARCHAR(50)   NOT NULL,
    applied_date DATE          NOT NULL,
    link         VARCHAR(255),
    location     VARCHAR(255),
    notes        VARCHAR(2000),
    user_id      BIGINT        NOT NULL REFERENCES app_users(id),
    deleted_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_preferences (
    user_id        BIGINT      PRIMARY KEY REFERENCES app_users(id),
    theme          VARCHAR(50) NOT NULL DEFAULT 'LIGHT',
    default_status VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS verification_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    token      VARCHAR(80)  NOT NULL UNIQUE,
    type       VARCHAR(50)  NOT NULL,
    user_id    BIGINT       NOT NULL REFERENCES app_users(id),
    expires_at TIMESTAMP    NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at    TIMESTAMP,
    payload    VARCHAR(255)
);
