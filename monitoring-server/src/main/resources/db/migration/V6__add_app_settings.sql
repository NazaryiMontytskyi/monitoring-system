CREATE TABLE app_settings (
    key   VARCHAR(100) PRIMARY KEY,
    value TEXT
);

INSERT INTO app_settings (key, value) VALUES
    ('notification.email.to',   'admin@example.com'),
    ('notification.email.from', 'monitoring@example.com');
