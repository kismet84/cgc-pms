-- Mirror of V309 for the frozen H2 regression chain: cross-instance scheduled job lease lock.

CREATE TABLE sys_scheduled_lock (
    lock_name   VARCHAR(100) NOT NULL,
    locked_by   VARCHAR(200) NOT NULL,
    locked_at   TIMESTAMP    NOT NULL,
    lease_until TIMESTAMP    NOT NULL,
    PRIMARY KEY (lock_name)
);
