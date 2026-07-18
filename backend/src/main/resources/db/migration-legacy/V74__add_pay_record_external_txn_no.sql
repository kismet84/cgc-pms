ALTER TABLE pay_record ADD COLUMN external_txn_no VARCHAR(128) NULL COMMENT '外部交易流水号';
ALTER TABLE pay_record ADD UNIQUE INDEX uk_external_txn_no (external_txn_no);
