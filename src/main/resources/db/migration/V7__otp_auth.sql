-- =====================================================================
-- V7__otp_auth.sql
-- Citizen authentication by phone OTP with auto-registration, plus the
-- rate limiting that makes an open front door safe.
--
-- Policy settled in design:
--   6-digit code, 15 minute validity, 3 attempts then burned,
--   60s resend cooldown, 5 sends per number per 24h, 20 per IP per hour,
--   auto-register on first successful verification,
--   5 open grievances per citizen.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Auto-registration: a citizen verifying a phone number for the first
-- time has not told us their name yet. Collect it when they file, or
-- never -- someone who only tracks a ticket need not identify further.
-- Collecting less personal data is also the right default under DPDP.
-- ---------------------------------------------------------------------
ALTER TABLE app_user ALTER COLUMN full_name DROP NOT NULL;

-- Staff and system rows must still be named: they appear in audit trails
-- and in citizen-facing "handled by" text.
ALTER TABLE app_user
    ADD CONSTRAINT staff_needs_name CHECK (
        user_type = 'CITIZEN' OR full_name IS NOT NULL
    );

-- ---------------------------------------------------------------------
-- OTP challenge: request metadata for rate limiting
--
-- The IP is personal data under the DPDP Act. It exists only to enforce
-- the per-IP send cap and is deleted with the challenge by the cleanup
-- job below -- never retained beyond that.
-- ---------------------------------------------------------------------
ALTER TABLE otp_challenge
    ADD COLUMN request_ip   INET,
    ADD COLUMN user_agent   VARCHAR(255),
    ADD COLUMN verified_at  TIMESTAMPTZ,   -- distinct from consumed_at:
                                           -- consumed = no longer usable
                                           -- (correct code, or burned)
                                           -- verified = the code matched
    ADD COLUMN burned_reason VARCHAR(30)
        CHECK (burned_reason IN ('MAX_ATTEMPTS','EXPIRED','SUPERSEDED','VERIFIED'));

-- A consumed challenge must say why it is finished.
ALTER TABLE otp_challenge
    ADD CONSTRAINT consumed_has_reason CHECK (
        (consumed_at IS NULL AND burned_reason IS NULL)
        OR
        (consumed_at IS NOT NULL AND burned_reason IS NOT NULL)
    );

-- 3 attempts then burned. V1 allowed any value.
ALTER TABLE otp_challenge
    ADD CONSTRAINT attempts_capped CHECK (attempts BETWEEN 0 AND 3);

-- Resend cooldown and the 24h per-number cap are both counting queries
-- over recent rows for one phone. V1's index already orders by
-- created_at DESC, which serves both.

-- Per-IP cap: count sends from one address in the last hour.
CREATE INDEX idx_otp_ip_recent ON otp_challenge (request_ip, created_at DESC)
    WHERE request_ip IS NOT NULL;

-- At most one live challenge per phone and purpose. A new send
-- supersedes the previous one (burned_reason = 'SUPERSEDED') rather
-- than leaving several codes valid at once.
CREATE UNIQUE INDEX uq_live_otp_per_phone
    ON otp_challenge (phone, purpose)
    WHERE consumed_at IS NULL;

-- ---------------------------------------------------------------------
-- Rate limit counters
--
-- Kept separate from otp_challenge so the cleanup job can delete old
-- challenges without losing the window a limit is counted over, and so
-- a limit can be enforced before any challenge row is written.
-- ---------------------------------------------------------------------
CREATE TABLE otp_rate_limit (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    scope_type  VARCHAR(10) NOT NULL CHECK (scope_type IN ('PHONE','IP')),
    scope_key   VARCHAR(45) NOT NULL,          -- E.164 phone, or IPv4/IPv6 text
    window_start TIMESTAMPTZ NOT NULL,
    send_count  INTEGER     NOT NULL DEFAULT 0 CHECK (send_count >= 0),
    blocked_until TIMESTAMPTZ,
    UNIQUE (scope_type, scope_key, window_start)
);
CREATE INDEX idx_rate_limit_lookup
    ON otp_rate_limit (scope_type, scope_key, window_start DESC);

-- ---------------------------------------------------------------------
-- Open grievance cap
--
-- The abuse control that replaces pre-registration: anyone with a
-- verified phone may file, but not without limit. Closed and rejected
-- grievances do not count, so a citizen whose complaints get resolved
-- can keep filing indefinitely.
--
-- Enforced in the service (it needs a counting query, which a CHECK
-- cannot do). This column allows a per-citizen override: a housing
-- society secretary or ward councillor may legitimately need more.
-- NULL means the configured default applies.
-- ---------------------------------------------------------------------
ALTER TABLE app_user
    ADD COLUMN open_grievance_limit SMALLINT
        CHECK (open_grievance_limit IS NULL OR open_grievance_limit > 0);

-- Counting a citizen's open grievances
CREATE INDEX idx_grv_citizen_open ON grievance (citizen_id)
    WHERE status NOT IN ('CLOSED','REJECTED');

-- ---------------------------------------------------------------------
-- Permission for raising a citizen's limit
-- ---------------------------------------------------------------------
INSERT INTO permission (code, description) VALUES
    ('CITIZEN_LIMIT_OVERRIDE', 'Raise or lower a citizen''s open grievance limit'),
    ('USER_SUSPEND',           'Lock or reinstate a user account')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code FROM role r CROSS JOIN permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code IN ('CITIZEN_LIMIT_OVERRIDE','USER_SUSPEND')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'CITIZEN_LIMIT_OVERRIDE' FROM role r
WHERE r.code = 'DEPT_ADMIN'
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- Audited account suspension
--
-- AppUser.setStatus() is currently a public setter, and status now gates
-- authorization on every path. Locking an account is a decision with
-- accountability attached and needs its own trail.
-- ---------------------------------------------------------------------
CREATE TABLE user_status_history (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id     UUID        NOT NULL REFERENCES app_user(id),
    from_status VARCHAR(20),                   -- NULL on creation
    to_status   VARCHAR(20) NOT NULL,
    actor_id    UUID        NOT NULL REFERENCES app_user(id),
    reason      VARCHAR(1000) NOT NULL CHECK (reason ~ '\S'),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_status_hist ON user_status_history (user_id, occurred_at);

CREATE TRIGGER trg_user_status_immutable
    BEFORE UPDATE OR DELETE ON user_status_history
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

-- ---------------------------------------------------------------------
-- Widen grievance_assignment.reason to match every other reason column
-- ---------------------------------------------------------------------
ALTER TABLE grievance_assignment
    ALTER COLUMN reason TYPE VARCHAR(1000);
