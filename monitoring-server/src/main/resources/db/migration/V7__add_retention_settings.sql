INSERT INTO app_settings (key, value) VALUES
    ('retention.enabled',              'true'),
    ('retention.metric_records.days',  '30'),
    ('retention.alert_events.days',    '90'),
    ('retention.report_history.days',  '180'),
    ('retention.cron',                 '0 0 3 * * *')
ON CONFLICT (key) DO NOTHING;
