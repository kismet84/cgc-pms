package com.cgcpms.communication;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommunicationMigrationH2Test {

    @Test
    void migrationCreatesTenantScopedConversationAndMessageConstraints() {
        Flyway beforeBackfill = Flyway.configure()
                .dataSource("jdbc:h2:mem:communication_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration-h2", "filesystem:src/main/resources/db/migration-h2-legacy",
                        "classpath:com/cgcpms/common/migration")
                .target("283")
                .cleanDisabled(false)
                .load();
        beforeBackfill.migrate();
        execute(beforeBackfill, """
                INSERT INTO sys_role(id,tenant_id,role_code,role_name,role_type,status,data_scope,deleted_flag)
                VALUES(990100,1001,'PROJECT_MANAGER','租户项目经理','CUSTOM','ENABLE','SELF',0),
                      (990101,1001,'COMMON_USER','租户普通用户','CUSTOM','ENABLE','SELF',0),
                      (990102,1001,'PM','历史项目经理','CUSTOM','ENABLE','SELF',0),
                      (990103,1001,'CSTM','历史成本经理','CUSTOM','ENABLE','SELF',0),
                      (990104,1001,'CM','历史商务经理','CUSTOM','ENABLE','SELF',0)
                """);
        execute(beforeBackfill, """
                INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,deleted_flag)
                VALUES(990300,1001,'legacy-role-user','x','历史角色用户','ENABLE',0,0)
                """);
        execute(beforeBackfill, """
                INSERT INTO sys_user_role(id,tenant_id,user_id,role_id)
                VALUES(990301,1001,990300,990102),(990302,1001,990300,990103),(990303,1001,990300,990104)
                """);
        execute(beforeBackfill, """
                INSERT INTO wf_template
                    (id,tenant_id,template_code,template_name,business_type,enabled,created_by,deleted_flag)
                VALUES(990400,0,'M89-LEGACY-PROJECT-ROLE-ENABLED','启用历史项目角色','TECH_ITEM',1,1,0),
                      (990401,0,'M89-LEGACY-PROJECT-ROLE-DISABLED','停用历史项目角色','TECH_ITEM',0,1,0)
                """);
        execute(beforeBackfill, """
                INSERT INTO wf_template_node
                    (id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,
                     approver_config,allow_transfer,allow_add_sign,timeout_hours,created_by,deleted_flag)
                VALUES(990410,0,990400,'LEGACY_PM','历史项目经理',1,'APPROVAL','OR_SIGN',
                       '{"type":"PROJECT_ROLE","roleCode":"PM"}' FORMAT JSON,1,1,24,1,0),
                      (990411,0,990401,'LEGACY_CSTM','历史成本经理',1,'APPROVAL','OR_SIGN',
                       '{"type":"PROJECT_ROLE","roleCode":"CSTM"}' FORMAT JSON,1,1,24,1,0)
                """);
        execute(beforeBackfill, """
                INSERT INTO wf_instance
                    (id,tenant_id,template_id,business_type,business_id,project_id,title,instance_status,
                     current_round,resubmit_count,business_revision,initiator_id,created_by,deleted_flag)
                VALUES(990420,0,990400,'TECH_ITEM',990420,10001,'历史项目角色运行实例','RUNNING',1,0,1,1,1,0)
                """);
        execute(beforeBackfill, """
                INSERT INTO wf_node_instance
                    (id,tenant_id,instance_id,template_node_id,node_code,node_name,node_order,approve_mode,
                     node_status,round_no,created_by,deleted_flag)
                VALUES(990421,0,990420,990410,'LEGACY_PM','历史项目经理',1,'OR_SIGN','ACTIVE',1,1,0)
                """);
        execute(beforeBackfill, """
                INSERT INTO sys_menu
                    (id,tenant_id,parent_id,menu_name,menu_type,perms,order_num,status,visible,deleted_flag)
                VALUES(990200,1001,0,'租户材料字典','MENU','material:dict:list',1,'ENABLE',1,0)
                """);
        Flyway flyway = Flyway.configure()
                .dataSource(beforeBackfill.getConfiguration().getDataSource())
                .locations("classpath:db/migration-h2", "filesystem:src/main/resources/db/migration-h2-legacy",
                        "classpath:com/cgcpms/common/migration")
                .cleanDisabled(false)
                .load();
        flyway.migrate();

        assertEquals("306", flyway.info().current().getVersion().getVersion());
        execute(flyway, """
                INSERT INTO communication_conversation(
                    id,tenant_id,type,direct_pair_key,status,created_at,updated_at)
                VALUES(990001,0,'DIRECT','10:20','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        execute(flyway, """
                INSERT INTO communication_member(
                    id,tenant_id,conversation_id,user_id,role,status,created_at,updated_at)
                VALUES(990011,0,990001,10,'MEMBER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        execute(flyway, """
                INSERT INTO communication_message(
                    id,tenant_id,conversation_id,sender_id,status,seq,client_message_id,created_at,updated_at)
                VALUES(990021,0,990001,10,'SENT',1,'client-msg-0001',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);

        assertThrows(SQLException.class, () -> executeChecked(flyway, """
                INSERT INTO communication_conversation(
                    id,tenant_id,type,direct_pair_key,status,created_at,updated_at)
                VALUES(990002,0,'DIRECT','10:20','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """));
        assertThrows(SQLException.class, () -> executeChecked(flyway, """
                INSERT INTO communication_message(
                    id,tenant_id,conversation_id,sender_id,status,seq,client_message_id,created_at,updated_at)
                VALUES(990022,0,990001,10,'SENT',2,'client-msg-0001',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """));
        assertEquals(3, scalar(flyway, """
                SELECT COUNT(*) FROM sys_menu
                WHERE tenant_id=0 AND perms IN (
                    'communication:view','communication:send','communication:group:manage')
                  AND deleted_flag=0
                """));
        assertEquals(3, scalar(flyway, """
                SELECT COUNT(*) FROM sys_menu
                WHERE tenant_id=1001 AND perms IN (
                    'communication:view','communication:send','communication:group:manage')
                  AND deleted_flag=0
                """));
        assertEquals(3, scalar(flyway, """
                SELECT COUNT(*) FROM sys_role_menu
                WHERE tenant_id=1001 AND role_id=990100
                """));
        assertEquals(2, scalar(flyway, """
                SELECT COUNT(*) FROM sys_role_menu role_menu
                JOIN sys_menu menu ON menu.id=role_menu.menu_id AND menu.tenant_id=role_menu.tenant_id
                WHERE role_menu.tenant_id=1001 AND role_menu.role_id=990101
                  AND menu.perms IN ('communication:view','communication:send')
                """));
        assertEquals(1, scalar(flyway, """
                SELECT COUNT(*) FROM wf_template_node
                WHERE id=990410 AND CAST(approver_config AS VARCHAR) LIKE '%PROJECT_ROLE%'
                  AND CAST(approver_config AS VARCHAR) LIKE '%PROJECT_MANAGER%'
                """));
        assertEquals(1, scalar(flyway, """
                SELECT COUNT(*) FROM wf_template_node
                WHERE id=990411 AND CAST(approver_config AS VARCHAR) LIKE '%PROJECT_ROLE%'
                  AND CAST(approver_config AS VARCHAR) LIKE '%CSTM%'
                """));
        assertEquals(1, scalar(flyway, """
                SELECT COUNT(*) FROM wf_node_instance
                WHERE id=990421 AND CAST(approver_config AS VARCHAR) LIKE '%PROJECT_ROLE%'
                  AND CAST(approver_config AS VARCHAR) LIKE '%PM%'
                """));
        assertEquals(2, scalar(flyway, """
                SELECT COUNT(DISTINCT role.role_code) FROM sys_user_role user_role
                JOIN sys_role role ON role.id=user_role.role_id AND role.tenant_id=user_role.tenant_id
                WHERE user_role.tenant_id=1001 AND user_role.user_id=990300
                  AND role.role_code IN ('PROJECT_MANAGER','PROJECT_ACCOUNTANT')
                """));
        assertEquals(0, scalar(flyway, """
                SELECT COUNT(*) FROM sys_role_menu role_menu
                JOIN sys_menu menu ON menu.id=role_menu.menu_id AND menu.tenant_id=role_menu.tenant_id
                WHERE role_menu.tenant_id=1001 AND role_menu.role_id=990101
                  AND menu.perms='communication:group:manage'
                """));
        assertEquals(1, scalar(flyway, """
                SELECT COUNT(*) FROM sys_menu
                WHERE tenant_id=1001 AND parent_id=990200
                  AND perms='material:dict:delete' AND deleted_flag=0
                """));
        assertEquals(2, scalar(flyway, """
                SELECT COUNT(*) FROM sys_role_menu role_menu
                JOIN sys_menu menu ON menu.id=role_menu.menu_id AND menu.tenant_id=role_menu.tenant_id
                WHERE role_menu.tenant_id=1001 AND menu.perms='material:dict:delete'
                """));
        assertEquals(2, scalar(flyway, """
                SELECT COUNT(*) FROM sys_role_menu role_menu
                JOIN sys_menu menu ON menu.id=role_menu.menu_id AND menu.tenant_id=role_menu.tenant_id
                JOIN sys_role role ON role.id=role_menu.role_id AND role.tenant_id=role_menu.tenant_id
                WHERE role_menu.tenant_id=1001 AND menu.perms='material:dict:delete'
                  AND role.role_code IN ('COMPANY_FINANCE','PROCUREMENT_LEAD')
                """));
    }

    private static long scalar(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void execute(Flyway flyway, String sql) {
        try {
            executeChecked(flyway, sql);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void executeChecked(Flyway flyway, String sql) throws SQLException {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
