ALTER TABLE bid_cost ADD CONSTRAINT ck_bid_cost_amounts CHECK (
    (ceiling_price IS NULL OR ceiling_price >= 0)
    AND (final_bid_price IS NULL OR final_bid_price >= 0)
);

ALTER TABLE pm_project ADD CONSTRAINT ck_pm_project_initiation_basis CHECK (
    initiation_basis IS NULL OR initiation_basis = 'BID_AWARD'
);
