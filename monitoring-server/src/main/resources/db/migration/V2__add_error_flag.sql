-- ============================================================
-- V2 — Add error_flag to metric_records
-- ============================================================
-- Adds a boolean index-friendly column that is set to TRUE
-- whenever a metric record represents an error state
-- (status DOWN/DEGRADED, or a non-null error_message).
--
-- This replaces the need for IS NOT NULL / LIKE filters on
-- error_message in COUNT queries used by the SLA engine.
-- ============================================================

ALTER TABLE metric_records
    ADD COLUMN IF NOT EXISTS error_flag BOOLEAN NOT NULL DEFAULT FALSE;

-- Back-fill existing rows: mark as error any record that
-- already had an error_message or a non-UP status.
UPDATE metric_records
   SET error_flag = TRUE
 WHERE error_message IS NOT NULL
    OR status IN ('DOWN', 'DEGRADED');
