package com.cgcpms.supplier.service;

import com.cgcpms.common.result.PageResult;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.supplier.dto.SupplierSourcingModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierSourcingQueryService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(readOnly = true)
    public WorkspacePage workspace(int eventPageNo, int performancePageNo, int returnPageNo,
                                   int pageSize, Long projectId) {
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        ProjectAccessChecker.ProjectSqlScope scope = projectAccessChecker.sqlScope();
        return new WorkspacePage(
                events(scope, eventPageNo, safePageSize, projectId),
                performance(scope, performancePageNo, safePageSize, projectId),
                returns(scope, returnPageNo, safePageSize, projectId));
    }

    @Transactional(readOnly = true)
    public PageResult<PerformanceCandidateRow> performanceCandidates(int pageNo, int pageSize,
                                                                     Long projectId) {
        int safePageSize = Math.min(200, Math.max(1, pageSize));
        ProjectAccessChecker.ProjectSqlScope scope = projectAccessChecker.sqlScope();
        QueryParts query = queryParts("""
                purchase_order.deleted_flag=0
                AND purchase_order.approval_status='APPROVED'
                AND purchase_order.contract_id IS NOT NULL
                AND purchase_order.partner_id IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM sp_performance_evaluation evaluation
                  WHERE evaluation.tenant_id=purchase_order.tenant_id
                   AND evaluation.purchase_order_id=purchase_order.id
                   AND evaluation.deleted_flag=0)
                """, scope, projectId);
        long total = count("mat_purchase_order purchase_order", query);
        List<PerformanceCandidateRow> records = jdbc.query("""
                SELECT purchase_order.id,purchase_order.project_id,purchase_order.order_code,
                 purchase_order.partner_id,partner.partner_code,partner.partner_name
                FROM mat_purchase_order purchase_order
                JOIN pm_project p ON p.id=purchase_order.project_id
                 AND p.tenant_id=purchase_order.tenant_id
                JOIN md_partner partner ON partner.id=purchase_order.partner_id
                 AND partner.tenant_id=purchase_order.tenant_id AND partner.deleted_flag=0
                WHERE %s
                ORDER BY purchase_order.created_at DESC,purchase_order.id DESC
                LIMIT ? OFFSET ?
                """.formatted(query.where()), (rs, rowNum) -> new PerformanceCandidateRow(
                id(rs, "id"), id(rs, "project_id"), rs.getString("order_code"),
                id(rs, "partner_id"), rs.getString("partner_code"), rs.getString("partner_name")),
                pageParameters(query.parameters(), pageNo, safePageSize));
        return page(pageNo, safePageSize, total, records);
    }

    private PageResult<SourcingEventRow> events(ProjectAccessChecker.ProjectSqlScope scope,
                                                int pageNo, int pageSize, Long projectId) {
        QueryParts query = queryParts("e.deleted_flag=0", scope, projectId);
        long total = count("sp_sourcing_event e", query);
        List<SourcingEventRow> records = jdbc.query("""
                SELECT e.id,e.project_id,e.purchase_request_id,e.sourcing_code,e.sourcing_title,
                 e.sourcing_type,e.deadline,e.currency_code,e.status,e.awarded_quote_id,
                 e.awarded_partner_id,e.contract_id,e.award_reason,e.version
                FROM sp_sourcing_event e
                JOIN pm_project p ON p.id=e.project_id AND p.tenant_id=e.tenant_id
                WHERE %s
                ORDER BY e.created_at DESC,e.id DESC
                LIMIT ? OFFSET ?
                """.formatted(query.where()), (rs, rowNum) -> new SourcingEventRow(
                id(rs, "id"), id(rs, "project_id"), id(rs, "purchase_request_id"),
                rs.getString("sourcing_code"), rs.getString("sourcing_title"),
                rs.getString("sourcing_type"), rs.getTimestamp("deadline").toLocalDateTime().toString(),
                rs.getString("currency_code"), rs.getString("status"),
                nullableId(rs, "awarded_quote_id"), nullableId(rs, "awarded_partner_id"),
                nullableId(rs, "contract_id"), rs.getString("award_reason"), rs.getInt("version")),
                pageParameters(query.parameters(), pageNo, pageSize));
        return page(pageNo, pageSize, total, records);
    }

    private PageResult<PerformanceRow> performance(ProjectAccessChecker.ProjectSqlScope scope,
                                                   int pageNo, int pageSize, Long projectId) {
        QueryParts query = queryParts("evaluation.deleted_flag=0", scope, projectId);
        long total = count("sp_performance_evaluation evaluation", query);
        List<PerformanceRow> records = jdbc.query("""
                SELECT evaluation.id,evaluation.project_id,evaluation.partner_id,
                 partner.partner_code,partner.partner_name,evaluation.contract_id,
                 evaluation.purchase_order_id,evaluation.evaluation_code,evaluation.period_start,
                 evaluation.period_end,evaluation.delivery_score,evaluation.quality_score,
                 evaluation.service_score,evaluation.commercial_score,evaluation.total_score,
                 evaluation.grade,evaluation.evaluation_comment,evaluation.recommend_blacklist,
                 evaluation.status
                FROM sp_performance_evaluation evaluation
                JOIN pm_project p ON p.id=evaluation.project_id AND p.tenant_id=evaluation.tenant_id
                LEFT JOIN md_partner partner ON partner.id=evaluation.partner_id
                 AND partner.tenant_id=evaluation.tenant_id AND partner.deleted_flag=0
                WHERE %s
                ORDER BY evaluation.period_end DESC,evaluation.id DESC
                LIMIT ? OFFSET ?
                """.formatted(query.where()), (rs, rowNum) -> new PerformanceRow(
                id(rs, "id"), id(rs, "project_id"), id(rs, "partner_id"),
                rs.getString("partner_code"), rs.getString("partner_name"),
                id(rs, "contract_id"), id(rs, "purchase_order_id"), rs.getString("evaluation_code"),
                rs.getDate("period_start").toLocalDate().toString(),
                rs.getDate("period_end").toLocalDate().toString(),
                decimal(rs, "delivery_score"), decimal(rs, "quality_score"),
                decimal(rs, "service_score"), decimal(rs, "commercial_score"),
                decimal(rs, "total_score"), rs.getString("grade"),
                rs.getString("evaluation_comment"), rs.getInt("recommend_blacklist"),
                rs.getString("status")), pageParameters(query.parameters(), pageNo, pageSize));
        return page(pageNo, pageSize, total, records);
    }

    private PageResult<SupplierReturnRow> returns(ProjectAccessChecker.ProjectSqlScope scope,
                                                  int pageNo, int pageSize, Long projectId) {
        QueryParts query = queryParts("""
                supplier_return.deleted_flag=0
                AND supplier_return.status IN ('CONFIRMED','REVERSED')
                AND EXISTS (SELECT 1 FROM sp_supplier_return_item item
                  WHERE item.tenant_id=supplier_return.tenant_id
                   AND item.return_id=supplier_return.id AND item.deleted_flag=0)
                """, scope, projectId);
        long total = count("sp_supplier_return supplier_return", query);
        List<SupplierReturnRow> records = jdbc.query("""
                SELECT supplier_return.id,supplier_return.project_id,supplier_return.partner_id,
                 partner.partner_code,partner.partner_name,supplier_return.contract_id,
                 supplier_return.purchase_order_id,supplier_return.receipt_id,
                 supplier_return.return_code,supplier_return.return_date,
                 supplier_return.return_quantity,supplier_return.return_amount,
                 supplier_return.reason,supplier_return.status
                FROM sp_supplier_return supplier_return
                JOIN pm_project p ON p.id=supplier_return.project_id
                 AND p.tenant_id=supplier_return.tenant_id
                LEFT JOIN md_partner partner ON partner.id=supplier_return.partner_id
                 AND partner.tenant_id=supplier_return.tenant_id AND partner.deleted_flag=0
                WHERE %s
                ORDER BY supplier_return.return_date DESC,supplier_return.id DESC
                LIMIT ? OFFSET ?
                """.formatted(query.where()), (rs, rowNum) -> new SupplierReturnRow(
                id(rs, "id"), id(rs, "project_id"), id(rs, "partner_id"),
                rs.getString("partner_code"), rs.getString("partner_name"),
                id(rs, "contract_id"), id(rs, "purchase_order_id"), id(rs, "receipt_id"),
                rs.getString("return_code"), rs.getDate("return_date").toLocalDate().toString(),
                decimal(rs, "return_quantity"), decimal(rs, "return_amount"),
                rs.getString("reason"), rs.getString("status")),
                pageParameters(query.parameters(), pageNo, pageSize));
        return page(pageNo, pageSize, total, records);
    }

    private long count(String table, QueryParts query) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM %s
                JOIN pm_project p ON p.id=%s.project_id AND p.tenant_id=%s.tenant_id
                WHERE %s
                """.formatted(table, alias(table), alias(table), query.where()),
                Long.class, query.parameters().toArray());
    }

    private static String alias(String table) {
        return table.substring(table.lastIndexOf(' ') + 1);
    }

    private static QueryParts queryParts(String domainPredicate,
                                         ProjectAccessChecker.ProjectSqlScope scope,
                                         Long projectId) {
        String where = "(" + domainPredicate.strip() + ") AND " + scope.predicate();
        List<Object> parameters = new ArrayList<>(scope.parameters());
        if (projectId != null) {
            where += " AND p.id=?";
            parameters.add(projectId);
        }
        return new QueryParts(where, List.copyOf(parameters));
    }

    private static Object[] pageParameters(List<Object> parameters, int pageNo, int pageSize) {
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(pageSize);
        pageParameters.add((Math.max(1, pageNo) - 1) * pageSize);
        return pageParameters.toArray();
    }

    private static <T> PageResult<T> page(int pageNo, int pageSize, long total, List<T> records) {
        return new PageResult<>(Math.max(1, pageNo), pageSize, total, records);
    }

    private static String id(ResultSet rs, String column) throws SQLException {
        return String.valueOf(rs.getLong(column));
    }

    private static String nullableId(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : String.valueOf(value);
    }

    private static String decimal(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column).toPlainString();
    }

    private record QueryParts(String where, List<Object> parameters) {}
}
