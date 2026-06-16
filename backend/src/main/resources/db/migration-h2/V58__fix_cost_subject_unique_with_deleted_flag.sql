-- V58__fix_cost_subject_unique_with_deleted_flag.sql
CREATE ALIAS IF NOT EXISTS DROP_COST_SUBJECT_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='COST_SUBJECT' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE COST_SUBJECT DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_COST_SUBJECT_UNIQUE();
DROP ALIAS DROP_COST_SUBJECT_UNIQUE;
ALTER TABLE cost_subject ADD UNIQUE KEY uk_cost_subject_code (tenant_id, subject_code, deleted_flag);
