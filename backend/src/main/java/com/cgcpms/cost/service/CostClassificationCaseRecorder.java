package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists recoverable unclassified work independently from the rejected business submission. */
@Service
public class CostClassificationCaseRecorder {
    private final JdbcTemplate jdbc;

    public CostClassificationCaseRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(Long tenantId, Long projectId, String sourceType, Long sourceId, Long sourceItemId,
                       String category, Long originalSubjectId, String errorCode, String errorMessage) {
        long itemId = sourceItemId == null ? 0L : sourceItemId;
        try {
            jdbc.update("""
                    INSERT INTO cost_unclassified_case
                    (id,tenant_id,project_id,source_type,source_id,source_item_id,business_category,
                     original_cost_subject_id,error_code,error_message,status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'OPEN')
                    """, IdWorker.getId(), tenantId, projectId, sourceType, sourceId, itemId,
                    category == null || category.isBlank() ? "*" : category, originalSubjectId,
                    errorCode, truncate(errorMessage));
        } catch (DuplicateKeyException duplicate) {
            jdbc.update("""
                    UPDATE cost_unclassified_case
                    SET project_id=?,business_category=?,original_cost_subject_id=?,error_code=?,error_message=?,
                        updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND source_type=? AND source_id=? AND source_item_id=? AND status='OPEN'
                    """, projectId, category == null || category.isBlank() ? "*" : category,
                    originalSubjectId, errorCode, truncate(errorMessage), tenantId, sourceType, sourceId, itemId);
        }
    }

    private static String truncate(String value) {
        String text = value == null ? "成本来源待归类" : value;
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
