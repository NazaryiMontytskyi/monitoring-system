ALTER TABLE alert_rules
    DROP COLUMN IF EXISTS predictive_enabled,
    DROP COLUMN IF EXISTS lookahead_minutes,
    DROP COLUMN IF EXISTS min_data_points;

ALTER TABLE alert_events
    DROP COLUMN IF EXISTS predictive,
    DROP COLUMN IF EXISTS predicted_breach_at,
    DROP COLUMN IF EXISTS confidence_score;
