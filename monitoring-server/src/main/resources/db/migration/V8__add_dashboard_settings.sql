INSERT INTO app_settings (key, value) VALUES ('dashboard.refresh.seconds', '7') ON CONFLICT (key) DO NOTHING;
