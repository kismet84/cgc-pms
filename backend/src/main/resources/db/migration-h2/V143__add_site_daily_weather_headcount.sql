-- H2 mirror: optional manual weather summary and on-site headcount.

ALTER TABLE site_daily_log
    ADD COLUMN weather_summary VARCHAR(200) NULL;

ALTER TABLE site_daily_log
    ADD COLUMN on_site_headcount INT NULL;
