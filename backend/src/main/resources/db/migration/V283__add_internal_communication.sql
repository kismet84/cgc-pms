CREATE TABLE communication_conversation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(100) NULL,
    direct_pair_key VARCHAR(80) NULL,
    owner_user_id BIGINT NULL,
    last_message_seq BIGINT NOT NULL DEFAULT 0,
    last_message_at DATETIME(3) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_communication_conversation_tenant_id (tenant_id, id),
    UNIQUE KEY uk_communication_direct_pair (tenant_id, direct_pair_key),
    KEY idx_communication_conversation_last (tenant_id, last_message_at),
    CONSTRAINT ck_communication_conversation_type CHECK (type IN ('DIRECT', 'GROUP')),
    CONSTRAINT ck_communication_conversation_status CHECK (status IN ('ACTIVE', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通讯会话';

CREATE TABLE communication_member (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    join_seq BIGINT NOT NULL DEFAULT 0,
    leave_seq BIGINT NULL,
    last_read_seq BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_communication_member (tenant_id, conversation_id, user_id),
    KEY idx_communication_member_user (tenant_id, user_id, status),
    CONSTRAINT fk_communication_member_conversation FOREIGN KEY (tenant_id, conversation_id)
        REFERENCES communication_conversation (tenant_id, id),
    CONSTRAINT ck_communication_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT ck_communication_member_status CHECK (status IN ('ACTIVE', 'LEFT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通讯成员';

CREATE TABLE communication_message (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    seq BIGINT NULL,
    body TEXT NULL,
    client_message_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_communication_message_idempotency (tenant_id, sender_id, client_message_id),
    UNIQUE KEY uk_communication_message_seq (tenant_id, conversation_id, seq),
    KEY idx_communication_message_history (tenant_id, conversation_id, status, seq),
    CONSTRAINT fk_communication_message_conversation FOREIGN KEY (tenant_id, conversation_id)
        REFERENCES communication_conversation (tenant_id, id),
    CONSTRAINT ck_communication_message_status CHECK (status IN ('DRAFT', 'SENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通讯消息';

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 28301, 0, 0, '站内通讯', 'MENU', '/communication', 'communication/index',
       'communication:view', 'message-square', 12, 'ENABLE', 1, 1, 1,
       'Internal communication entry', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id=0 AND perms='communication:view' AND deleted_flag=0
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 28302, 0, 28301, '发送消息', 'BUTTON', NULL, NULL,
       'communication:send', NULL, 1, 'ENABLE', 0, 1, 1,
       'Internal communication send authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id=0 AND perms='communication:send' AND deleted_flag=0
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 28303, 0, 28301, '管理群聊', 'BUTTON', NULL, NULL,
       'communication:group:manage', NULL, 2, 'ENABLE', 0, 1, 1,
       'Internal communication group authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id=0 AND perms='communication:group:manage' AND deleted_flag=0
);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 283000000000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.tenant_id=r.tenant_id
    AND m.perms IN ('communication:view', 'communication:send') AND m.deleted_flag=0
WHERE r.tenant_id=0 AND r.status='ENABLE' AND r.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id=r.tenant_id AND rm.role_id=r.id AND rm.menu_id=m.id
  );

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 283100000000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.tenant_id=r.tenant_id
    AND m.perms='communication:group:manage' AND m.deleted_flag=0
WHERE r.tenant_id=0 AND UPPER(r.role_code) IN ('PROJECT_MANAGER','DEPARTMENT_MANAGER','GENERAL_MANAGER')
  AND r.status='ENABLE' AND r.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id=r.tenant_id AND rm.role_id=r.id AND rm.menu_id=m.id
  );
