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
                      (990101,1001,'COMMON_USER','租户普通用户','CUSTOM','ENABLE','SELF',0)
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

        assertEquals("288", flyway.info().current().getVersion().getVersion());
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
        assertEquals(0, scalar(flyway, """
                SELECT COUNT(*) FROM sys_role_menu role_menu
                JOIN sys_menu menu ON menu.id=role_menu.menu_id AND menu.tenant_id=role_menu.tenant_id
                WHERE role_menu.tenant_id=1001 AND menu.perms='material:dict:delete'
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
