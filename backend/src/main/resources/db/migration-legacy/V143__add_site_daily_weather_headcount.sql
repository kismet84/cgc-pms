-- Extend the existing daily log with two optional, manually entered facts.

ALTER TABLE site_daily_log
    ADD COLUMN weather_summary VARCHAR(200) NULL COMMENT '人工天气摘要' AFTER next_day_plan,
    ADD COLUMN on_site_headcount INT NULL COMMENT '在场人数；NULL 未填写，0 明确无人' AFTER weather_summary;
