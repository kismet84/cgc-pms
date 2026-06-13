-- V46__add_purchase_item_material_index.sql
-- 建筑工程总包项目全过程管理系统 - 采购申请明细表物料ID索引
-- 说明：为 mat_purchase_request_item(material_id) 添加索引，优化按物料维度查询采购申请的效率。
--       该索引在 V35 建表时已定义（KEY idx_mpi_material），本迁移作为独立索引保障，确保所有环境一致。
-- 数据库：H2 (由 MySQL 迁移自动转换)

CREATE INDEX IF NOT EXISTS idx_mpi_material ON mat_purchase_request_item(material_id);
