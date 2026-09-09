-- 定时任务跨实例互斥锁。
-- 之前每个定时任务只有 JVM 内的 AtomicBoolean 兜底，多实例部署时同一任务会并行执行
-- （月度分摊重复入账、成本汇总重复 DELETE+INSERT）。这里用租约锁做跨实例互斥。
-- 该表不属于任何租户，只经 JdbcTemplate 访问，因此不进入租户插件。

CREATE TABLE sys_scheduled_lock (
    lock_name   VARCHAR(100) NOT NULL COMMENT '任务名，全局唯一',
    locked_by   VARCHAR(200) NOT NULL COMMENT '持有者实例标识',
    locked_at   DATETIME     NOT NULL COMMENT '获取时间',
    lease_until DATETIME     NOT NULL COMMENT '租约到期时间，过期后其他实例可抢占',
    PRIMARY KEY (lock_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='定时任务跨实例互斥锁';
