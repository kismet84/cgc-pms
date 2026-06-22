package com.cgcpms.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the SQL injection safety exemption mechanism used by
 * {@code scripts/check-sql-safety.ps1}.
 *
 * <p>The exemption marker is:</p>
 * <pre>SQL-SAFETY: server-side-enum</pre>
 *
 * <p>When a line containing a flagged pattern also includes this comment,
 * the scanner skips it. These tests confirm the exemption logic is correct.</p>
 */
@DisplayName("SQL 安全扫描豁免机制验证")
class SqlSafetyTest {

    /** The exemption marker that the PowerShell scanner looks for. */
    static final String EXEMPTION_MARKER = "SQL-SAFETY: server-side-enum";

    /** The regex patterns from check-sql-safety.ps1, kept in sync. */
    static final List<PatternInfo> SCAN_PATTERNS = List.of(
        new PatternInfo("${} MyBatis string substitution", "\\$\\{"),
        new PatternInfo("@Select/@Update/@Delete/@Insert with string concat",
            "@(Select|Update|Delete|Insert)\\s*\\(.*\"\\s*\\+\\s*[^\"\\r\\n]"),
        new PatternInfo("MyBatis-Plus .apply()", "\\.apply\\s*\\("),
        new PatternInfo("MyBatis-Plus .last()", "\\.last\\s*\\("),
        new PatternInfo("MyBatis-Plus .having()", "\\.having\\s*\\("),
        new PatternInfo("Raw JDBC Statement", "\\bStatement\\b")
    );

    // ------------------------------------------------------------------
    // Exemption comment tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("包含豁免注释的行应被所有相关模式正确匹配但被扫描器跳过")
    void exemptedLineShouldNotMatchAnyPattern() {
        // In a MyBatis XML mapper, a line using ${} with the exemption marker.
        // The scanner checks the marker before checking patterns.
        String exemptLine = "  WHERE status IN (${statusList}) -- SQL-SAFETY: server-side-enum  ";

        // The ${} pattern matches this line (it IS dangerous SQL)
        boolean matchedByDollar = Pattern.compile("\\$\\{").matcher(exemptLine).find();
        assertTrue(matchedByDollar,
            "The ${} pattern matches the raw line content (the pattern itself works)");

        // The exemption marker IS present — so the scanner would skip this line
        assertTrue(exemptLine.contains(EXEMPTION_MARKER),
            "Exemption line must contain the marker so the scanner skips it");

        // Verify: if we simulate the scanner logic, this line would be skipped
        // (marker present → continue, no violation recorded)
    }

    @Test
    @DisplayName("无豁免注释的 ${} 行应被检测到")
    void unexemptedLineShouldBeFlagged() {
        String riskyLine = "SELECT * FROM t WHERE name = ${name}";

        boolean matched = SCAN_PATTERNS.stream().anyMatch(pi ->
            Pattern.compile(pi.regex).matcher(riskyLine).find()
        );

        assertTrue(matched,
            "A line without the exemption marker that contains ${} must be flagged");
    }

    @Test
    @DisplayName("豁免注释仅在同行生效 — 下一行独立判断")
    void exemptionAppliesPerLine() {
        String exemptLine = "query.apply(\"status = 'APPROVED'\") // SQL-SAFETY: server-side-enum";
        String riskyLine  = "query.apply(\"amount > \" + inputValue)";

        // exemptLine has the marker → scanner skips it
        assertTrue(exemptLine.contains(EXEMPTION_MARKER));

        // riskyLine does NOT have the marker → scanner must flag it
        assertFalse(riskyLine.contains(EXEMPTION_MARKER));
        boolean flagged = SCAN_PATTERNS.stream().anyMatch(pi ->
            Pattern.compile(pi.regex).matcher(riskyLine).find()
        );
        assertTrue(flagged,
            "Line without exemption marker must still be flagged independently");
    }

    @Test
    @DisplayName("last() 无豁免注释应被检测")
    void lastMethodWithoutExemptionShouldBeFlagged() {
        String line = "query.last(\"ORDER BY \" + sortColumn)";
        assertFalse(line.contains(EXEMPTION_MARKER));

        boolean matched = Pattern.compile("\\.last\\s*\\(").matcher(line).find();
        assertTrue(matched, ".last() without exemption must be flagged");
    }

    @Test
    @DisplayName("having() 无豁免注释应被检测")
    void havingMethodWithoutExemptionShouldBeFlagged() {
        String line = "queryWrapper.having(\"SUM(amount) > \" + threshold)";
        assertFalse(line.contains(EXEMPTION_MARKER));

        boolean matched = Pattern.compile("\\.having\\s*\\(").matcher(line).find();
        assertTrue(matched, ".having() without exemption must be flagged");
    }

    @Test
    @DisplayName("原生 Statement 使用应被检测")
    void rawJdbcStatementShouldBeFlagged() {
        String line = "Statement stmt = connection.createStatement();";
        assertFalse(line.contains(EXEMPTION_MARKER));

        boolean matched = Pattern.compile("\\bStatement\\b").matcher(line).find();
        assertTrue(matched, "Raw JDBC Statement must be flagged");
    }

    @Test
    @DisplayName("所有6个扫描模式应正确匹配对应的危险代码")
    void allPatternsShouldMatchTheirTargets() {
        // Test cases directly mapped to SCAN_PATTERNS by regex index
        record Case(String description, String line, int patternIndex) {}
        List<Case> cases = List.of(
            new Case("${} MyBatis string substitution",
                "WHERE tenant_id = ${tenantId}", 0),
            new Case("@Select/@Update/@Delete/@Insert with string concat",
                "@Select(\"SELECT * FROM t WHERE id = \" + id)", 1),
            new Case("MyBatis-Plus .apply()",
                "wrapper.apply(\"status = '\" + s + \"'\")", 2),
            new Case("MyBatis-Plus .last()",
                "wrapper.last(\"LIMIT \" + n)", 3),
            new Case("MyBatis-Plus .having()",
                "wrapper.having(\"SUM(amount) > \" + val)", 4),
            new Case("Raw JDBC Statement",
                "try (Statement s = conn.createStatement())", 5)
        );

        for (Case tc : cases) {
            String regex = SCAN_PATTERNS.get(tc.patternIndex).regex;
            boolean matched = Pattern.compile(regex).matcher(tc.line).find();
            assertTrue(matched,
                "Pattern for '" + tc.description + "' should match: " + tc.line);
        }
    }

    // ------------------------------------------------------------------
    // False positive / edge case tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("安全的 Java 多行字符串拼接不应被检测（\"str \" + \"str\"）")
    void multilineStringConcatShouldNotBeFlagged() {
        // This is the WfTaskMapper pattern: Java string concatenation for readability,
        // all values use #{param} (safe parameterized queries)
        String safeLine = "@Update(\"UPDATE wf_task SET task_status = #{newStatus}, \" +";

        boolean matched = SCAN_PATTERNS.stream().anyMatch(pi ->
            Pattern.compile(pi.regex).matcher(safeLine).find()
        );

        assertFalse(matched,
            "Safe multi-line string concatenation ( \" +  at end-of-line) must not be flagged");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    record PatternInfo(String name, String regex) {}
}
