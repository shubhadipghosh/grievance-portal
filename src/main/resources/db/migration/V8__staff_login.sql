-- =====================================================================
-- V8__staff_login.sql
-- Staff password login: case-insensitive email, and at most 5 failed
-- attempts per email per 15 minutes.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Email is case-insensitive
--
-- The application lowercases email on write and on lookup, so someone
-- typing Ravi@city.gov.in reaches the account stored as ravi@city.gov.in.
-- Existing rows are brought into line, and two accounts may never differ
-- only by case.
-- ---------------------------------------------------------------------
UPDATE app_user SET email = lower(email) WHERE email <> lower(email);

-- Login looks up lower(input), so a mixed-case email written past the
-- application (seed script, manual fix) could never be matched: a silent
-- lockout. Reject it at write time instead.
ALTER TABLE app_user
    ADD CONSTRAINT email_lowercase CHECK (email = lower(email));

-- With email_lowercase, the existing UNIQUE (email) already rules out
-- case duplicates. This index states the rule directly and keeps holding
-- if the CHECK is ever relaxed.
CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (lower(email));

-- ---------------------------------------------------------------------
-- Rate limits are no longer only about OTP
--
-- V7's otp_rate_limit now also counts failed staff logins, so it becomes
-- rate_limit, and send_count becomes attempt_count. The mechanism is
-- unchanged -- a counter per key over a fixed window, checked and
-- incremented by one upsert.
--
-- Constraint names do not follow a table rename. Postgres 18 also names
-- NOT NULL constraints, so every otp_rate_limit_* name is renamed here.
-- Renaming the primary key and unique constraints renames their indexes.
-- ---------------------------------------------------------------------
ALTER TABLE otp_rate_limit RENAME TO rate_limit;
ALTER TABLE rate_limit RENAME COLUMN send_count TO attempt_count;

ALTER TABLE rate_limit RENAME CONSTRAINT otp_rate_limit_pkey TO rate_limit_pkey;
ALTER TABLE rate_limit RENAME CONSTRAINT otp_rate_limit_scope_type_scope_key_window_start_key
    TO rate_limit_scope_type_scope_key_window_start_key;
ALTER TABLE rate_limit RENAME CONSTRAINT otp_rate_limit_send_count_check TO rate_limit_attempt_count_check;
ALTER TABLE rate_limit RENAME CONSTRAINT otp_rate_limit_id_not_null TO rate_limit_id_not_null;
ALTER TABLE rate_limit RENAME CONSTRAINT otp_rate_limit_scope_type_not_null TO rate_limit_scope_type_not_null;
ALTER TABLE rate_limit RENAME CONSTRAINT otp_rate_limit_scope_key_not_null TO rate_limit_scope_key_not_null;
ALTER TABLE rate_limit RENAME CONSTRAINT otp_rate_limit_window_start_not_null TO rate_limit_window_start_not_null;
ALTER TABLE rate_limit RENAME CONSTRAINT otp_rate_limit_send_count_not_null TO rate_limit_attempt_count_not_null;

-- ---------------------------------------------------------------------
-- Staff login attempts
--
-- For scope STAFF_LOGIN, scope_key is the lowercased email and
-- attempt_count counts failed logins (a successful login does not
-- count). A failed attempt counts even when no account has that email,
-- so being rate limited reveals nothing about whether an account exists.
-- ---------------------------------------------------------------------

-- Room for an email (app_user.email is VARCHAR(255)) and the new scope name.
ALTER TABLE rate_limit
    ALTER COLUMN scope_type TYPE VARCHAR(20),
    ALTER COLUMN scope_key  TYPE VARCHAR(255);

ALTER TABLE rate_limit DROP CONSTRAINT otp_rate_limit_scope_type_check;
ALTER TABLE rate_limit
    ADD CONSTRAINT rate_limit_scope_type_check
        CHECK (scope_type IN ('PHONE','IP','STAFF_LOGIN'));
