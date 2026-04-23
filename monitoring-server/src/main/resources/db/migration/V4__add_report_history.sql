CREATE TABLE report_history (
    id           BIGSERIAL PRIMARY KEY,
    service_id   BIGINT REFERENCES registered_services(id) ON DELETE CASCADE,
    report_type  VARCHAR(32) NOT NULL,
    period_from  TIMESTAMP NOT NULL,
    period_to    TIMESTAMP NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    file_size_kb INT
);
