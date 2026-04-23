ALTER TABLE metric_records
    ADD COLUMN IF NOT EXISTS non_heap_used_mb  BIGINT,
    ADD COLUMN IF NOT EXISTS threads_live       INT,
    ADD COLUMN IF NOT EXISTS threads_daemon     INT,
    ADD COLUMN IF NOT EXISTS gc_pause_ms        DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS process_cpu_usage  DOUBLE PRECISION;
