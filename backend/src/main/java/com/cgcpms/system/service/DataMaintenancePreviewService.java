package com.cgcpms.system.service;

import com.cgcpms.system.dto.DataMaintenancePreview;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToLongFunction;

@Service
@RequiredArgsConstructor
public class DataMaintenancePreviewService {
    private static final String OBJECTS_SQL = """
            SELECT TABLE_NAME, TABLE_TYPE
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
            ORDER BY TABLE_NAME
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DataMaintenanceTablePolicy policy;

    @Transactional(readOnly = true)
    public DataMaintenancePreview preview() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        List<DatabaseObject> objects = jdbcTemplate.query(OBJECTS_SQL,
                (rs, rowNum) -> new DatabaseObject(rs.getString(1), rs.getString(2)));
        return classify(database, objects, this::countRows);
    }

    DataMaintenancePreview classify(
            String database,
            List<DatabaseObject> objects,
            ToLongFunction<String> rowCounter
    ) {
        Map<String, DataMaintenanceTablePolicy.Group> manifest = new HashMap<>();
        for (DataMaintenanceTablePolicy.Group group : policy.groups()) {
            for (String table : group.tables()) manifest.put(normalize(table), group);
        }

        Set<String> actualTables = new TreeSet<>();
        List<String> ignoredViews = new ArrayList<>();
        for (DatabaseObject object : objects) {
            if ("VIEW".equalsIgnoreCase(object.type())) ignoredViews.add(object.name());
            else if ("BASE TABLE".equalsIgnoreCase(object.type())) actualTables.add(normalize(object.name()));
        }

        List<String> blockers = new ArrayList<>();
        actualTables.stream().filter(table -> !manifest.containsKey(table))
                .forEach(table -> blockers.add("UNKNOWN_BASE_TABLE:" + table));
        manifest.keySet().stream().filter(table -> !actualTables.contains(table)).sorted()
                .forEach(table -> blockers.add("MISSING_BASE_TABLE:" + table));

        Map<String, MutableCount> retained = new LinkedHashMap<>();
        policy.groups().stream()
                .filter(group -> group.disposition() == DataMaintenanceTablePolicy.Disposition.RETAIN)
                .forEach(group -> retained.put(group.code(), new MutableCount()));
        int clearTableCount = 0;
        long clearRowCount = 0;
        long sysFileCount = 0;

        for (String table : actualTables) {
            DataMaintenanceTablePolicy.Group group = manifest.get(table);
            if (group == null) continue;
            if (group.disposition() == DataMaintenanceTablePolicy.Disposition.RETAIN) {
                retained.get(group.code()).tables++;
            } else {
                clearTableCount++;
            }
            long rows;
            try {
                rows = rowCounter.applyAsLong(table);
            } catch (RuntimeException ex) {
                blockers.add("COUNT_FAILED:" + table);
                continue;
            }
            if (group.disposition() == DataMaintenanceTablePolicy.Disposition.RETAIN) {
                retained.get(group.code()).rows += rows;
            } else {
                clearRowCount += rows;
            }
            if ("sys_file".equals(table)) sysFileCount = rows;
        }

        List<DataMaintenancePreview.RetainedGroupCount> retainedGroups = retained.entrySet().stream()
                .map(entry -> new DataMaintenancePreview.RetainedGroupCount(
                        entry.getKey(), entry.getValue().tables, entry.getValue().rows))
                .toList();
        return new DataMaintenancePreview(database, policy.fingerprint(), blockers.isEmpty(), List.copyOf(blockers),
                retainedGroups, clearTableCount, clearRowCount, sysFileCount, List.copyOf(ignoredViews));
    }

    private long countRows(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Long.class);
        return count == null ? 0 : count;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    record DatabaseObject(String name, String type) { }

    private static final class MutableCount {
        private int tables;
        private long rows;
    }
}
