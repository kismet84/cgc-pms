-- V51__fix_project_unique_with_deleted_flag.sql
-- Add deleted_flag to pm_project unique key so logically-deleted projects can be re-created
-- H2 does not support ALTER TABLE DROP UNIQUE (columns); use inline alias to find and drop auto-generated name
CREATE ALIAS IF NOT EXISTS DROP_PM_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='PM_PROJECT' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE PM_PROJECT DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_PM_UNIQUE();
DROP ALIAS DROP_PM_UNIQUE;
ALTER TABLE pm_project ADD UNIQUE KEY uk_pm_project_code (tenant_id, project_code, deleted_flag);
