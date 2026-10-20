-- =====================================================================
-- V6__reroute_and_view_permission.sql
-- Adds the audit trail for re-routing a grievance, and an explicit
-- permission for seeing one.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Re-routing audit
-- Changing a grievance's department changes who may act on it, so every
-- change must be attributable. grievance_status_history covers status
-- changes only; re-routing does not change status.
--
-- Old/new ward is recorded even though triage cannot change ward: a
-- future admin ward correction belongs in this same table, not a second
-- one. All six columns are NOT NULL because the grievance's category,
-- department and ward are; an unchanged pair simply repeats the value.
-- ---------------------------------------------------------------------
CREATE TABLE grievance_reroute (
    id                UUID PRIMARY KEY DEFAULT uuidv7(),
    grievance_id      UUID          NOT NULL REFERENCES grievance(id),
    actor_id          UUID          NOT NULL REFERENCES app_user(id),
    old_category_id   UUID          NOT NULL REFERENCES category(id),
    new_category_id   UUID          NOT NULL REFERENCES category(id),
    old_department_id UUID          NOT NULL REFERENCES department(id),
    new_department_id UUID          NOT NULL REFERENCES department(id),
    old_ward_id       UUID          NOT NULL REFERENCES ward(id),
    new_ward_id       UUID          NOT NULL REFERENCES ward(id),
    reason            VARCHAR(1000) NOT NULL,
    occurred_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- A reroute that changes nothing is a bug, not an audit entry.
    CONSTRAINT reroute_changes_something CHECK (
        old_category_id   <> new_category_id
        OR old_department_id <> new_department_id
        OR old_ward_id       <> new_ward_id
    ),

    -- NOT NULL accepts whitespace. A reroute changes who may act on the
    -- grievance, so the reason must be real text.
    CONSTRAINT reroute_reason_not_blank CHECK (btrim(reason) <> '')
);
CREATE INDEX idx_reroute_grv ON grievance_reroute (grievance_id, occurred_at);

CREATE TRIGGER trg_reroute_immutable
    BEFORE UPDATE OR DELETE ON grievance_reroute
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

-- ---------------------------------------------------------------------
-- Visibility permission
-- The API layer gates every grievance endpoint on "can this actor see
-- it" before anything else (not visible -> 404). Staff visibility is an
-- explicit permission, not "holds any grant in scope": the SYSTEM user
-- holds an unscoped grant to auto-close and must be able to do nothing
-- else, including read.
-- ---------------------------------------------------------------------
INSERT INTO permission (code, description) VALUES
    ('GRIEVANCE_VIEW', 'View a grievance')
ON CONFLICT (code) DO NOTHING;

-- Every human staff role. Deliberately not SYSTEM.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'GRIEVANCE_VIEW' FROM role r
WHERE r.code IN ('SUPER_ADMIN','DEPT_ADMIN','FIELD_OFFICER','AUDITOR')
ON CONFLICT DO NOTHING;
