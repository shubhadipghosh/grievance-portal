-- ---------------------------------------------------------------------
-- Staff administration: first-login password change, audited revocation
-- ---------------------------------------------------------------------

-- Set when an admin creates the account with a generated password. Until the
-- user picks their own, every endpoint but /auth/me, /auth/password and
-- logout refuses them.
ALTER TABLE app_user
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;

-- Who revoked a grant and why; granted_by already records the other half.
ALTER TABLE user_role_grant
    ADD COLUMN revoked_by    UUID REFERENCES app_user(id),
    ADD COLUMN revoke_reason VARCHAR(1000) CHECK (revoke_reason ~ '\S');

-- All three revocation columns are set together or not at all. NOT VALID:
-- rows revoked before this migration have no revoker to record.
ALTER TABLE user_role_grant
    ADD CONSTRAINT grant_revocation_complete
        CHECK ((revoked_at IS NULL) = (revoked_by IS NULL)
           AND (revoked_at IS NULL) = (revoke_reason IS NULL)) NOT VALID;

-- A grant is never edited or deleted: the one permitted change is revoking
-- an active grant. Anything else would rewrite who could do what, and when.
CREATE OR REPLACE FUNCTION grant_revoke_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'user_role_grant rows cannot be deleted; revoke instead';
    END IF;
    IF OLD.revoked_at IS NOT NULL
       OR NEW.revoked_at IS NULL
       OR (NEW.id, NEW.user_id, NEW.role_id, NEW.department_id, NEW.ward_id, NEW.granted_by, NEW.granted_at)
          IS DISTINCT FROM
          (OLD.id, OLD.user_id, OLD.role_id, OLD.department_id, OLD.ward_id, OLD.granted_by, OLD.granted_at)
    THEN
        RAISE EXCEPTION 'user_role_grant rows can only be revoked, once';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_grant_revoke_only
    BEFORE UPDATE OR DELETE ON user_role_grant
    FOR EACH ROW EXECUTE FUNCTION grant_revoke_only();
