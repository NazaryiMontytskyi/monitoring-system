-- ============================================================
-- V1 — Initial schema for the Monitoring Server
-- ============================================================
-- Tables created:
--   registered_services   — catalogue of monitored microservices
--   sla_definitions       — SLA contract per service (1:1)
--   metric_records        — time-series measurements (push + pull)
--   alert_rules           — configurable alert thresholds per service
--   alert_events          — history of fired alerts
-- ============================================================

-- ----------------------------------------------------------------
-- registered_services
-- ----------------------------------------------------------------
CREATE TABLE registered_services (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(128)  NOT NULL UNIQUE,
    host           VARCHAR(255)  NOT NULL,
    port           INTEGER       NOT NULL,
    actuator_url   VARCHAR(512),
    base_url       VARCHAR(512),
    status         VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN'
                       CHECK (status IN ('UP', 'DEGRADED', 'DOWN', 'UNKNOWN')),
    registered_at  TIMESTAMP     NOT NULL,
    last_seen_at   TIMESTAMP
);

-- ----------------------------------------------------------------
-- sla_definitions  (1:1 with registered_services)
-- ----------------------------------------------------------------
CREATE TABLE sla_definitions (
    service_id              BIGINT         PRIMARY KEY
                                REFERENCES registered_services (id) ON DELETE CASCADE,
    uptime_percent          DOUBLE PRECISION NOT NULL DEFAULT 99.9,
    max_response_time_ms    BIGINT           NOT NULL DEFAULT 1000,
    max_error_rate_percent  DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    description             VARCHAR(255)
);

-- ----------------------------------------------------------------
-- metric_records
-- ----------------------------------------------------------------
CREATE TABLE metric_records (
    id               BIGSERIAL PRIMARY KEY,
    service_id       BIGINT        NOT NULL
                         REFERENCES registered_services (id) ON DELETE CASCADE,
    endpoint         VARCHAR(255),
    response_time_ms BIGINT        NOT NULL DEFAULT 0,
    status           VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN'
                         CHECK (status IN ('UP', 'DEGRADED', 'DOWN', 'UNKNOWN')),
    cpu_usage        DOUBLE PRECISION,
    heap_used_mb     BIGINT,
    heap_max_mb      BIGINT,
    error_message    TEXT,
    anomaly          BOOLEAN       NOT NULL DEFAULT FALSE,
    z_score          DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    source           VARCHAR(8)    NOT NULL DEFAULT 'PULL'
                         CHECK (source IN ('PULL', 'PUSH')),
    recorded_at      TIMESTAMP     NOT NULL
);

-- Index for time-range queries and anomaly detection window
CREATE INDEX idx_metric_records_service_time
    ON metric_records (service_id, recorded_at DESC);

-- ----------------------------------------------------------------
-- alert_rules
-- ----------------------------------------------------------------
CREATE TABLE alert_rules (
    id           BIGSERIAL PRIMARY KEY,
    service_id   BIGINT        NOT NULL
                     REFERENCES registered_services (id) ON DELETE CASCADE,
    metric_type  VARCHAR(32)   NOT NULL
                     CHECK (metric_type IN (
                         'RESPONSE_TIME_AVG',
                         'STATUS_DOWN',
                         'CPU_USAGE',
                         'UPTIME_PERCENT',
                         'ERROR_RATE'
                     )),
    comparator   VARCHAR(4)    NOT NULL
                     CHECK (comparator IN ('GT', 'LT')),
    threshold    DOUBLE PRECISION NOT NULL,
    enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    cooldown_minutes INTEGER   NOT NULL DEFAULT 15,
    created_at   TIMESTAMP     NOT NULL
);

-- ----------------------------------------------------------------
-- alert_events
-- ----------------------------------------------------------------
CREATE TABLE alert_events (
    id                 BIGSERIAL PRIMARY KEY,
    rule_id            BIGINT    NOT NULL
                           REFERENCES alert_rules (id) ON DELETE CASCADE,
    service_id         BIGINT    NOT NULL
                           REFERENCES registered_services (id) ON DELETE CASCADE,
    fired_at           TIMESTAMP NOT NULL,
    metric_value       DOUBLE PRECISION NOT NULL,
    message            TEXT,
    notification_sent  BOOLEAN   NOT NULL DEFAULT FALSE
);

-- Index for event log queries filtered by service and time
CREATE INDEX idx_alert_events_service_time
    ON alert_events (service_id, fired_at DESC);
