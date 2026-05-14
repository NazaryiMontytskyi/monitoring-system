ALTER TABLE alert_rules
    ALTER COLUMN comparator TYPE VARCHAR(8);

ALTER TABLE alert_rules
    DROP CONSTRAINT IF EXISTS alert_rules_comparator_check;

ALTER TABLE alert_rules
    ADD CONSTRAINT alert_rules_comparator_check
        CHECK (comparator IN ('GT', 'LT', 'GTE', 'LTE', 'EQ'));
