ALTER TABLE bid_cost ADD COLUMN planned_start_date DATE NULL;
ALTER TABLE bid_cost ADD COLUMN planned_end_date DATE NULL;
ALTER TABLE bid_cost ADD CONSTRAINT ck_bid_cost_planned_dates CHECK (
    planned_start_date IS NULL OR planned_end_date IS NULL OR planned_start_date <= planned_end_date
);
