INSERT INTO app_settings (key, value) VALUES ('notification.email.enabled', 'true') ON CONFLICT (key) DO NOTHING;
