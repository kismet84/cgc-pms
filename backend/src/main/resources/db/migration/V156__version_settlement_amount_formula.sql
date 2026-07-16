ALTER TABLE stl_settlement
    ADD COLUMN amount_formula_version VARCHAR(64) NOT NULL DEFAULT 'LEGACY_UNVERIFIED'
    COMMENT '结算金额口径版本；历史数据核对后方可回填当前版本';
