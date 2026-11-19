-- ---------------------------------------------------------------------
-- Grant scope rules
-- A NULL axis means "every department/ward", so a grant missing its
-- department silently reaches the whole municipality. Each role now says
-- which axes it must, may or must not set, and every staff member belongs
-- to at most one department: all their active scoped grants agree on it.
-- ---------------------------------------------------------------------

ALTER TABLE role
    ADD COLUMN department_scope VARCHAR(10) NOT NULL DEFAULT 'OPTIONAL'
        CHECK (department_scope IN ('REQUIRED', 'FORBIDDEN', 'OPTIONAL')),
    ADD COLUMN ward_scope       VARCHAR(10) NOT NULL DEFAULT 'OPTIONAL'
        CHECK (ward_scope IN ('REQUIRED', 'FORBIDDEN', 'OPTIONAL'));

UPDATE role SET department_scope = 'FORBIDDEN', ward_scope = 'FORBIDDEN' WHERE code IN ('SUPER_ADMIN', 'SYSTEM');
UPDATE role SET department_scope = 'REQUIRED',  ward_scope = 'FORBIDDEN' WHERE code = 'DEPT_ADMIN';
UPDATE role SET department_scope = 'REQUIRED',  ward_scope = 'REQUIRED'  WHERE code = 'FIELD_OFFICER';
-- AUDITOR stays OPTIONAL on both: municipality-wide, one department, or one ward.

CREATE OR REPLACE FUNCTION scope_allows(rule VARCHAR, axis UUID) RETURNS boolean AS $$
    SELECT CASE rule WHEN 'REQUIRED'  THEN axis IS NOT NULL
                     WHEN 'FORBIDDEN' THEN axis IS NULL
                     ELSE true END;
$$ LANGUAGE sql IMMUTABLE;

-- Grants already issued must meet the rules too, or the invariant is a lie.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM user_role_grant g JOIN role r ON r.id = g.role_id
               WHERE g.revoked_at IS NULL
                 AND NOT (scope_allows(r.department_scope, g.department_id)
                          AND scope_allows(r.ward_scope, g.ward_id))) THEN
        RAISE EXCEPTION 'Active grants violate the role scope rules; revoke them before migrating';
    END IF;
    IF EXISTS (SELECT 1 FROM user_role_grant
               WHERE revoked_at IS NULL AND department_id IS NOT NULL
               GROUP BY user_id HAVING count(DISTINCT department_id) > 1) THEN
        RAISE EXCEPTION 'Some users hold active grants in more than one department; revoke them before migrating';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION grant_scope_check() RETURNS trigger AS $$
DECLARE
    r     role%ROWTYPE;
    other UUID;
BEGIN
    SELECT * INTO r FROM role WHERE id = NEW.role_id;
    IF NOT (scope_allows(r.department_scope, NEW.department_id) AND scope_allows(r.ward_scope, NEW.ward_id)) THEN
        RAISE EXCEPTION 'role % needs department %, ward %', r.code, r.department_scope, r.ward_scope;
    END IF;
    IF NEW.department_id IS NOT NULL THEN
        -- Serialize grants to one user, so two concurrent grants in different
        -- departments cannot both pass the check below.
        PERFORM 1 FROM app_user WHERE id = NEW.user_id FOR NO KEY UPDATE;
        SELECT department_id INTO other FROM user_role_grant
        WHERE user_id = NEW.user_id AND revoked_at IS NULL
          AND department_id IS NOT NULL AND department_id <> NEW.department_id
        LIMIT 1;
        IF FOUND THEN
            RAISE EXCEPTION 'user % already belongs to department %', NEW.user_id, other;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Insert only: trg_grant_revoke_only already forbids changing a grant's scope.
CREATE TRIGGER trg_grant_scope
    BEFORE INSERT ON user_role_grant
    FOR EACH ROW EXECUTE FUNCTION grant_scope_check();
