-- =====================================================================
-- V10__history_hold_reason_type.sql
-- Records the hold category on the ON_HOLD history row. grievance.
-- hold_reason_type is cleared when the hold ends, so without this a past
-- hold has no category, and citizens are shown the category, never the
-- free-text reason (STATE_MACHINE.md, "What citizens see").
--
-- Nullable: rows written before V10 have no category, and the table is
-- append-only, so they cannot be backfilled.
-- =====================================================================

ALTER TABLE grievance_status_history
    ADD COLUMN hold_reason_type VARCHAR(30)
        CHECK (hold_reason_type IN ('AWAITING_CITIZEN','AWAITING_CONTRACTOR',
                                    'AWAITING_DEPARTMENT','AWAITING_BUDGET','OTHER')),
    ADD CONSTRAINT hist_hold_reason_only_on_hold
        CHECK (hold_reason_type IS NULL OR to_status = 'ON_HOLD');
