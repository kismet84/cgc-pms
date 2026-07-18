ALTER TABLE pay_record ADD COLUMN external_txn_no VARCHAR(128) NULL;
CREATE UNIQUE INDEX uk_external_txn_no ON pay_record(external_txn_no);
