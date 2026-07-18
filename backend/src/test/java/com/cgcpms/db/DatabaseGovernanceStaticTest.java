package com.cgcpms.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseGovernanceStaticTest {
    private static final Path ROOT = Path.of("src");

    @Test
    void newMysqlTablesHaveTableAndColumnComments() throws IOException {
        Path migrations = ROOT.resolve("main/resources/db/migration");
        String followUpComments = Files.readString(migrations.resolve("V215__document_cost_subject_v2_schema.sql"));
        Pattern table = Pattern.compile("CREATE\\s+TABLE\\s+([a-zA-Z0-9_]+)\\s*\\((.*?)\\)\\s*ENGINE=.*?;",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        for (Path file : Files.list(migrations).filter(p -> version(p) >= 195).toList()) {
            String sql = Files.readString(file);
            Matcher matcher = table.matcher(sql);
            while (matcher.find()) {
                String statement = matcher.group();
                assertTrue(statement.matches("(?is).*COMMENT\\s*=.*"), file + " 新表缺少表注释");
                String tableName = matcher.group(1);
                for (String line : matcher.group(2).split("\\R")) {
                    String value = line.trim();
                    if (!value.matches("(?is)^[a-z_][a-z0-9_]*\\s+(BIGINT|INT|TINYINT|VARCHAR|DECIMAL|DATETIME|DATE|TIMESTAMP|LONGTEXT|TEXT|JSON)\\b.*")) continue;
                    String columnName = value.substring(0, value.indexOf(' '));
                    assertTrue(value.matches("(?is).*\\bCOMMENT\\b.*")
                                    || followUpDocumentsColumn(followUpComments, tableName, columnName),
                            file + " 新字段缺少注释: " + value);
                }
            }
        }
    }

    private static boolean followUpDocumentsColumn(String sql, String tableName, String columnName) {
        Matcher alter = Pattern.compile("(?is)ALTER\\s+TABLE\\s+" + Pattern.quote(tableName) + "\\s+(.*?);")
                .matcher(sql);
        if (!alter.find()) return false;
        return Pattern.compile("(?is)MODIFY\\s+COLUMN\\s+" + Pattern.quote(columnName) + "\\b.*?COMMENT\\b")
                .matcher(alter.group(1)).find();
    }

    @Test
    void jsonPayloadsAreConstrainedAndProfilesValidateMigrations() throws IOException {
        String mysql = Files.readString(ROOT.resolve("main/resources/db/migration/V206__validate_json_payloads.sql"));
        assertEquals(18, occurrences(mysql, "ADD CONSTRAINT"));
        assertTrue(mysql.contains("JSON_VALID"));
        assertTrue(mysql.contains("1048576"));
        for (String profile : List.of("application-local.yml", "application-dev.yml", "application-prod.yml")) {
            String yaml = Files.readString(ROOT.resolve("main/resources").resolve(profile));
            assertTrue(yaml.contains("validate-on-migrate: true"), profile + " 必须启用 Flyway validate");
        }
    }

    @Test
    void runtimeResourcesContainNoLegacyBakSchemas() throws IOException {
        try (var paths = Files.walk(ROOT.resolve("main/resources"))) {
            assertTrue(paths.noneMatch(p -> p.getFileName().toString().endsWith(".bak")));
        }
    }

    @Test
    void obsoleteDeletedTokensAreRetiredSymmetricallyWithoutDroppingLegacyTables() throws IOException {
        List<String> tables = List.of(
                "sys_user", "sys_role", "md_partner", "ct_contract", "ct_contract_change",
                "org_company", "org_position", "pay_application", "md_material",
                "mat_purchase_order", "mat_receipt", "mat_stock", "pm_project", "cost_subject");
        for (Path migration : List.of(
                ROOT.resolve("main/resources/db/migration/V210__drop_obsolete_deleted_tokens.sql"),
                ROOT.resolve("main/resources/db/migration-h2/V210__drop_obsolete_deleted_tokens.sql"))) {
            String sql = Files.readString(migration).toLowerCase();
            assertEquals(14, occurrences(sql, "drop column deleted_token"), migration + " 必须精确退役 14 个字段");
            for (String table : tables) {
                assertTrue(sql.contains("alter table " + table + " drop column deleted_token"),
                        migration + " 缺少 " + table + ".deleted_token");
            }
            assertFalse(sql.contains("drop table"), migration + " 不得夹带表退役");
            assertFalse(sql.contains("drop column active_unique_token"), migration + " 不得删除权威活动唯一键");
            assertFalse(sql.contains("overhead_allocation_record"), migration + " 历史表仍受生产依赖确认门保护");
        }
    }

    @Test
    void rawSelectStarCannotRegressAndWorkflowBypassQueriesAreExplicit() throws IOException {
        Path javaRoot = ROOT.resolve("main/java/com/cgcpms");
        int count;
        try (var files = Files.walk(javaRoot)) {
            count = files.filter(p -> p.toString().endsWith(".java"))
                    .mapToInt(p -> {
                        try { return occurrences(Files.readString(p).toUpperCase(), "SELECT *"); }
                        catch (IOException e) { throw new IllegalStateException(e); }
                    }).sum();
        }
        // 基线包含已合入的项目收尾、技术方案、供应商招采与财务模块；后续只能下降。
        assertTrue(count <= 221, "原始 SELECT * 数量不得回升，当前=" + count);
        for (String mapper : List.of("WfInstanceMapper.java", "WfTaskMapper.java")) {
            String java = Files.readString(javaRoot.resolve("workflow/mapper").resolve(mapper)).toUpperCase();
            assertFalse(java.contains("SELECT *"), mapper + " 的租户绕过查询必须使用显式投影");
        }
    }

    @Test
    void governanceAssetsCloseLegacyAmbiguityWithoutDestructiveAutomation() throws IOException {
        Path workspace = Path.of("..").toAbsolutePath().normalize();
        String standards = Files.readString(workspace.resolve("docs/database/database-design-standards.md"));
        assertAll(
                () -> assertTrue(standards.contains("永久幂等事实")),
                () -> assertTrue(standards.contains("活动唯一键")),
                () -> assertTrue(standards.contains("1 MiB")),
                () -> assertTrue(standards.contains("1,000 万行")),
                () -> assertTrue(standards.contains("SELECT *"))
        );

        for (String script : List.of("database-remediation-preflight-v194.sql", "database-remediation-postflight.sql")) {
            String sql = Files.readString(workspace.resolve("scripts/database").resolve(script));
            String executable = sql.replaceAll("(?m)^\\s*--.*$", "").toUpperCase();
            assertFalse(Pattern.compile("(?im)^\\s*(INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE)\\b")
                    .matcher(executable).find(), script + " 必须保持只读");
        }

        String archiveReadme = Files.readString(
                workspace.resolve("docs/archive/database/legacy-h2-bootstrap/README.md"));
        assertTrue(archiveReadme.contains("不参与任何运行时加载"));

        String frontend = Files.readString(workspace.resolve("frontend-admin/src/pages/system/dict/index.vue"));
        assertTrue(frontend.contains("cssClass"), "css_class 当前仍有真实管理端消费者，不得按过期审计结论删除");
        try (var files = Files.walk(ROOT.resolve("main/java"))) {
            assertTrue(files.filter(p -> p.toString().endsWith(".java"))
                    .noneMatch(p -> {
                        try { return Files.readString(p).contains("overhead_allocation_record"); }
                        catch (IOException e) { throw new IllegalStateException(e); }
                    }), "遗留 overhead_allocation_record 禁止重新接入运行时代码");
        }
    }

    private static int version(Path path) {
        Matcher matcher = Pattern.compile("^V(\\d+)__").matcher(path.getFileName().toString());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static int occurrences(String value, String needle) {
        int count = 0, offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) { count++; offset += needle.length(); }
        return count;
    }
}
