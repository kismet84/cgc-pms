package com.cgcpms.measurement.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.measurement.dto.MeasurementModels.*;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.revenue.dto.RevenueOperationsModels.OwnerSettlementRequest;
import com.cgcpms.revenue.service.RevenueOperationsService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductionMeasurementService {
    private static final Set<String> INTERNALLY_APPROVED_STATUSES = Set.of(
            "INTERNAL_APPROVED", "OWNER_SUBMITTED", "OWNER_RETURNED", "OWNER_CONFIRMED", "SETTLEMENT_CREATED");
    private static final int CODE_GENERATION_MAX_RETRIES = 3;
    private static final DateTimeFormatter MONTH_CODE = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbc;
    private final WorkflowEngine workflowEngine;
    private final RevenueOperationsService revenueOperationsService;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createPeriod(PeriodRequest request) {
        requireMainContract(request.projectId(), request.contractId());
        if (request.startDate().isAfter(request.endDate()) || request.cutoffDate().isBefore(request.startDate())) {
            throw error("MEASUREMENT_PERIOD_DATE_INVALID", "计量周期起止日或截止日不合法");
        }
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO measurement_period(id,tenant_id,project_id,contract_id,period_code,period_name,start_date,end_date,cutoff_date,
                     status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,'OPEN',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), request.projectId(), request.contractId(), request.periodCode().trim(),
                    request.periodName().trim(), request.startDate(), request.endDate(), request.cutoffDate(), user(), user(), request.remark());
        } catch (DuplicateKeyException e) {
            throw error("MEASUREMENT_PERIOD_DUPLICATE", "同一合同的计量周期编码不能重复");
        }
        return period(id);
    }

    public List<Map<String, Object>> periods(Long projectId, Long contractId, LocalDate startDate, LocalDate endDate) {
        validateDateWindow(startDate, endDate);
        List<Long> projectIds = projectIdsForQuery(projectId, "查看计量周期");
        if (projectIds.isEmpty()) return List.of();
        List<Object> params = new ArrayList<>();
        params.add(tenant());
        params.addAll(projectIds);
        Collections.addAll(params, contractId, contractId, startDate, startDate, endDate, endDate);
        return jdbc.queryForList("""
                SELECT p.* FROM measurement_period p
                 WHERE p.tenant_id=? AND p.deleted_flag=0
                   AND p.project_id IN (%s) AND (? IS NULL OR p.contract_id=?)
                   AND (? IS NULL OR p.end_date>=?) AND (? IS NULL OR p.start_date<=?)
                 ORDER BY p.start_date DESC,p.id DESC
                """.formatted(placeholders(projectIds.size())), params.toArray());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> closePeriod(Long id, Integer version) {
        Map<String, Object> row = requirePeriod(id, true);
        requireVersion(version, row, "MEASUREMENT_PERIOD");
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM production_measurement WHERE tenant_id=? AND period_id=? AND deleted_flag=0 AND status IN('DRAFT','REJECTED','PENDING','OWNER_SUBMITTED')", Integer.class, tenant(), id);
        if (active != null && active > 0) throw error("MEASUREMENT_PERIOD_HAS_ACTIVE_DOCUMENT", "存在未完成计量或业主报量，周期不能关闭");
        int updated = jdbc.update("UPDATE measurement_period SET status='CLOSED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='OPEN' AND version=?", user(), id, tenant(), version);
        if (updated != 1) throw error("MEASUREMENT_PERIOD_CONCURRENT_UPDATE", "计量周期版本冲突，请刷新后重试");
        return period(longValue(row.get("id")));
    }

    public List<Map<String, Object>> sources(Long projectId, Long contractId) {
        requireMainContract(projectId, contractId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : jdbc.queryForList("SELECT id,item_code,item_name,item_spec,unit,quantity,unit_price,amount FROM ct_contract_item WHERE tenant_id=? AND contract_id=? AND deleted_flag=0 AND quantity>0 AND amount>=0 ORDER BY sort_order,id", tenant(), contractId)) {
            BigDecimal measured = approvedQuantity("CONTRACT_ITEM", longValue(item.get("id")), null);
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("sourceType", "CONTRACT_ITEM"); row.put("sourceId", item.get("id"));
            row.put("contractQuantity", quantity(item.get("quantity"))); row.put("unitPrice", price(item.get("unit_price")));
            row.put("approvedQuantity", measured); row.put("remainingQuantity", quantity(item.get("quantity")).subtract(measured));
            result.add(row);
        }
        for (Map<String, Object> change : jdbc.queryForList("SELECT id,change_code item_code,change_name item_name,change_amount FROM ct_contract_change WHERE tenant_id=? AND project_id=? AND contract_id=? AND deleted_flag=0 AND approval_status='APPROVED' AND effective_flag=1 AND change_amount>0 ORDER BY created_at,id", tenant(), projectId, contractId)) {
            BigDecimal measured = approvedQuantity("CONTRACT_CHANGE", null, longValue(change.get("id")));
            Map<String, Object> row = new LinkedHashMap<>(change);
            row.put("sourceType", "CONTRACT_CHANGE"); row.put("sourceId", change.get("id")); row.put("unit", "项");
            row.put("contractQuantity", quantity(BigDecimal.ONE)); row.put("unitPrice", price(change.get("change_amount")));
            row.put("approvedQuantity", measured); row.put("remainingQuantity", quantity(BigDecimal.ONE).subtract(measured));
            result.add(row);
        }
        return moneyPayload(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createMeasurement(MeasurementRequest request) {
        requireMainContract(request.projectId(), request.contractId());
        Map<String, Object> period = requirePeriod(request.periodId(), true);
        if (!Objects.equals(longValue(period.get("project_id")), request.projectId()) || !Objects.equals(longValue(period.get("contract_id")), request.contractId())) {
            throw error("MEASUREMENT_PERIOD_CONTEXT_MISMATCH", "计量周期不属于所选项目合同");
        }
        if (!"OPEN".equals(string(period.get("status")))) throw error("MEASUREMENT_PERIOD_CLOSED", "已关闭计量周期不能新增计量");
        if (request.measureDate().isBefore(localDate(period.get("start_date"))) || request.measureDate().isAfter(localDate(period.get("cutoff_date")))) {
            throw error("MEASUREMENT_DATE_OUTSIDE_PERIOD", "计量日期必须位于周期开始日至截止日之间");
        }
        Long id = IdWorker.getId();
        BigDecimal currentTotal = money(BigDecimal.ZERO);
        int sort = 0;
        Set<String> sources = new HashSet<>();
        List<ResolvedLine> resolved = new ArrayList<>();
        for (MeasurementLineRequest line : request.lines()) {
            ResolvedLine value = resolveLine(request.contractId(), line);
            String key = value.sourceType() + ":" + value.sourceId();
            if (!sources.add(key)) throw error("MEASUREMENT_SOURCE_DUPLICATE", "同一清单项或变更不能在计量单中重复");
            resolved.add(value);
            currentTotal = currentTotal.add(value.currentAmount());
        }
        BigDecimal priorTotal = money(jdbc.queryForObject("SELECT COALESCE(SUM(current_reported_amount),0) FROM production_measurement WHERE tenant_id=? AND contract_id=? AND deleted_flag=0 AND status IN('INTERNAL_APPROVED','OWNER_SUBMITTED','OWNER_RETURNED','OWNER_CONFIRMED','SETTLEMENT_CREATED')", BigDecimal.class, tenant(), request.contractId()));
        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            String measureCode = nextMeasurementCode(request.measureDate(), attempt);
            try {
                jdbc.update("""
                        INSERT INTO production_measurement(id,tenant_id,project_id,contract_id,period_id,measure_code,measure_date,
                         current_reported_amount,cumulative_reported_amount,status,approval_status,attachment_count,formula_version,version,
                         created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                        VALUES(?,?,?,?,?,?,?, ?,?,'DRAFT','DRAFT',?,'PRODUCTION_MEASUREMENT_V1',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                        """, id, tenant(), request.projectId(), request.contractId(), request.periodId(), measureCode, request.measureDate(),
                        currentTotal, priorTotal.add(currentTotal), 0, user(), user(), request.remark());
                for (ResolvedLine line : resolved) {
                    jdbc.update("""
                            INSERT INTO production_measurement_line(id,tenant_id,measurement_id,source_type,contract_item_id,contract_change_id,
                             item_code,item_name,item_spec,unit,contract_quantity,prior_approved_quantity,current_reported_quantity,
                             cumulative_reported_quantity,unit_price,current_reported_amount,cumulative_reported_amount,evidence_count,sort_order,created_by,created_at)
                            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                            """, IdWorker.getId(), tenant(), id, line.sourceType(), line.contractItemId(), line.contractChangeId(),
                            line.itemCode(), line.itemName(), line.itemSpec(), line.unit(), line.contractQuantity(), line.priorQuantity(),
                            line.currentQuantity(), line.priorQuantity().add(line.currentQuantity()), line.unitPrice(), line.currentAmount(),
                            money(line.priorQuantity().multiply(line.unitPrice())).add(line.currentAmount()), 0, sort++, user());
                }
                return measurement(id);
            } catch (DuplicateKeyException e) {
                if (duplicateOf(e, "uk_production_measure_code")) continue;
                throw error("MEASUREMENT_PERIOD_DOCUMENT_DUPLICATE", "同一合同周期只能存在一张产值计量单");
            }
        }
        throw error("PRODUCTION_MEASUREMENT_CODE_CONFLICT", "计量编号生成冲突，请重试");
    }

    public List<Map<String, Object>> measurements(Long projectId, String status, LocalDate startDate, LocalDate endDate) {
        validateDateWindow(startDate, endDate);
        List<Long> projectIds = projectIdsForQuery(projectId, "查看产值计量");
        if (projectIds.isEmpty()) return List.of();
        List<Object> params = new ArrayList<>();
        params.add(tenant());
        params.addAll(projectIds);
        Collections.addAll(params, status, status, startDate, startDate, endDate, endDate);
        return moneyPayload(jdbc.queryForList("""
                SELECT m.*,p.period_code,p.period_name FROM production_measurement m JOIN measurement_period p ON p.id=m.period_id
                 WHERE m.tenant_id=? AND m.deleted_flag=0 AND m.project_id IN (%s) AND (? IS NULL OR m.status=?)
                   AND (? IS NULL OR m.measure_date>=?) AND (? IS NULL OR m.measure_date<=?)
                 ORDER BY m.measure_date DESC,m.id DESC
                """.formatted(placeholders(projectIds.size())), params.toArray()));
    }

    public Map<String, Object> measurement(Long id) {
        Map<String, Object> header = one("SELECT m.*,p.period_code,p.period_name FROM production_measurement m JOIN measurement_period p ON p.id=m.period_id WHERE m.id=? AND m.tenant_id=? AND m.deleted_flag=0", id, tenant());
        if (header == null) throw error("PRODUCTION_MEASUREMENT_NOT_FOUND", "产值计量单不存在");
        projectAccessChecker.checkAccess(longValue(header.get("project_id")), "查看产值计量");
        Map<String, Object> result = new LinkedHashMap<>(header);
        result.put("lines", jdbc.queryForList("SELECT * FROM production_measurement_line WHERE tenant_id=? AND measurement_id=? ORDER BY sort_order,id", tenant(), id));
        result.put("submissions", jdbc.queryForList("SELECT * FROM owner_measurement_submission WHERE tenant_id=? AND measurement_id=? AND deleted_flag=0 ORDER BY revision_no", tenant(), id));
        return moneyPayload(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitMeasurement(Long id, Integer version) {
        Map<String, Object> row = requireMeasurement(id, true);
        requireVersion(version, row, "PRODUCTION_MEASUREMENT");
        String status = string(row.get("status"));
        if (!Set.of("DRAFT", "REJECTED").contains(status)) throw error("PRODUCTION_MEASUREMENT_NOT_SUBMITTABLE", "只有草稿或驳回状态可以提交");
        Map<String, Object> period = requirePeriod(longValue(row.get("period_id")), true);
        if (!"OPEN".equals(period.get("status"))) throw error("MEASUREMENT_PERIOD_CLOSED", "计量周期已关闭，禁止提交");
        int attachmentCount = validateAndSyncEvidence(id);
        WfInstance instance;
        Long existing = longValue(row.get("approval_instance_id"));
        if ("REJECTED".equals(status) && existing != null) instance = workflowEngine.resubmitProductionMeasurement(existing, user(), UserContext.getCurrentUsername());
        else instance = workflowEngine.submitProductionMeasurement(user(), UserContext.getCurrentUsername(), tenant(), WorkflowBusinessTypes.PRODUCTION_MEASUREMENT,
                id, string(row.get("measure_code")), decimal(row.get("current_reported_amount")), longValue(row.get("project_id")),
                longValue(row.get("contract_id")), "产值计量", null, null);
        int submitted = jdbc.update("UPDATE production_measurement SET status='PENDING',approval_status='PENDING',approval_instance_id=?,attachment_count=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND version=?", instance.getId(), attachmentCount, user(), id, tenant(), version);
        if (submitted != 1) throw error("PRODUCTION_MEASUREMENT_CONCURRENT_UPDATE", "产值计量版本冲突，请刷新后重试");
        return measurement(id);
    }

    /** Workflow handler also calls this method so persisted client/count fields can never bypass real-file gates. */
    @Transactional(rollbackFor = Exception.class)
    public int validateAndSyncEvidence(Long id) {
        Map<String, Object> row = requireMeasurement(id, true);
        if (!Set.of("DRAFT", "REJECTED").contains(string(row.get("status")))) {
            throw error("PRODUCTION_MEASUREMENT_NOT_SUBMITTABLE", "只有草稿或驳回状态可以提交");
        }
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT * FROM production_measurement_line WHERE tenant_id=? AND measurement_id=? ORDER BY id FOR UPDATE", tenant(), id);
        if (lines.isEmpty()) throw error("PRODUCTION_MEASUREMENT_LINE_REQUIRED", "计量单至少包含一条明细");
        int attachmentCount = cleanFileCount("PRODUCTION_MEASUREMENT", id, "MEASUREMENT_GENERAL");
        if (attachmentCount < 1) throw error("PRODUCTION_MEASUREMENT_ATTACHMENT_REQUIRED", "计量单必须上传真实且扫描通过的总体计量依据");
        for (Map<String, Object> line : lines) {
            int evidenceCount = cleanFileCount("PRODUCTION_MEASUREMENT", id, "ML_" + line.get("id"));
            if (evidenceCount < 1) throw error("PRODUCTION_MEASUREMENT_LINE_EVIDENCE_REQUIRED", "每条计量明细都必须有真实且扫描通过的现场完成依据");
            jdbc.update("UPDATE production_measurement_line SET evidence_count=? WHERE id=? AND tenant_id=?", evidenceCount, line.get("id"), tenant());
            revalidateCapacity(id, line);
        }
        jdbc.update("UPDATE production_measurement SET attachment_count=? WHERE id=? AND tenant_id=?", attachmentCount, id, tenant());
        return attachmentCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        Map<String, Object> row = requireMeasurement(id, true);
        if (INTERNALLY_APPROVED_STATUSES.contains(string(row.get("status")))) return;
        if (jdbc.update("UPDATE production_measurement SET status='INTERNAL_APPROVED',approval_status='APPROVED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant()) != 1) {
            throw error("PRODUCTION_MEASUREMENT_APPROVAL_STATE_INVALID", "产值计量审批状态不正确");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        jdbc.update("UPDATE production_measurement SET status='REJECTED',approval_status='REJECTED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitToOwner(Long measurementId, Integer version, OwnerSubmissionRequest request) {
        Map<String, Object> measurement = requireMeasurement(measurementId, true);
        requireVersion(version, measurement, "PRODUCTION_MEASUREMENT");
        if (!Set.of("INTERNAL_APPROVED", "OWNER_RETURNED").contains(string(measurement.get("status")))) {
            throw error("OWNER_SUBMISSION_MEASUREMENT_STATE_INVALID", "只有内部审批通过或业主退回的计量单可以报送业主");
        }
        int attachmentCount = cleanFileCount("PRODUCTION_MEASUREMENT", measurementId, "OWNER_SUBMISSION");
        if (attachmentCount < 1) throw error("OWNER_SUBMISSION_ATTACHMENT_REQUIRED", "业主申报必须上传真实且扫描通过的报送附件");
        Integer revision = jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM owner_measurement_submission WHERE tenant_id=? AND measurement_id=? AND deleted_flag=0", Integer.class, tenant(), measurementId);
        Long id = IdWorker.getId();
        String measureCode = string(measurement.get("measure_code"));
        String submissionCode = measureCode != null && measureCode.matches("PM-\\d{6}-\\d{3}")
                ? "OMS-" + measureCode.substring(3) + "-R" + revision
                : "OMS-" + measurementId + "-R" + revision;
        jdbc.update("""
                INSERT INTO owner_measurement_submission(id,tenant_id,project_id,contract_id,measurement_id,submission_code,revision_no,
                 submitted_at,external_document_no,submitted_amount,confirmed_amount,deducted_amount,status,attachment_count,version,
                 created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,?,0,0,'SUBMITTED',?,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                """, id, tenant(), measurement.get("project_id"), measurement.get("contract_id"), measurementId,
                submissionCode, revision, blankToNull(request.externalDocumentNo()),
                measurement.get("current_reported_amount"), attachmentCount, user(), user(), request.remark());
        for (Map<String, Object> line : jdbc.queryForList("SELECT id,current_reported_quantity,current_reported_amount FROM production_measurement_line WHERE tenant_id=? AND measurement_id=? ORDER BY id", tenant(), measurementId)) {
            jdbc.update("INSERT INTO owner_measurement_review_line(id,tenant_id,submission_id,measurement_line_id,submitted_quantity,submitted_amount,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",
                    IdWorker.getId(), tenant(), id, line.get("id"), line.get("current_reported_quantity"), line.get("current_reported_amount"), user(), user());
        }
        int submitted = jdbc.update("UPDATE production_measurement SET status='OWNER_SUBMITTED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND version=?", user(), measurementId, tenant(), version);
        if (submitted != 1) throw error("PRODUCTION_MEASUREMENT_CONCURRENT_UPDATE", "产值计量版本冲突，请刷新后重试");
        return submission(id);
    }

    public List<Map<String, Object>> submissions(Long projectId, String status, LocalDate startDate, LocalDate endDate) {
        validateDateWindow(startDate, endDate);
        List<Long> projectIds = projectIdsForQuery(projectId, "查看业主报量");
        if (projectIds.isEmpty()) return List.of();
        List<Object> params = new ArrayList<>();
        params.add(tenant());
        params.addAll(projectIds);
        Collections.addAll(params, status, status, startDate, startDate, endDate, endDate);
        String sql = "SELECT s.*,m.measure_code,p.period_code FROM owner_measurement_submission s "
                + "JOIN production_measurement m ON m.id=s.measurement_id "
                + "JOIN measurement_period p ON p.id=m.period_id "
                + "WHERE s.tenant_id=? AND s.deleted_flag=0 AND s.project_id IN (" + placeholders(projectIds.size()) + ") "
                + "AND (? IS NULL OR s.status=?) AND (? IS NULL OR m.measure_date>=?) "
                + "AND (? IS NULL OR m.measure_date<=?) ORDER BY s.submitted_at DESC,s.id DESC";
        return moneyPayload(jdbc.queryForList(sql, params.toArray()));
    }

    public Map<String, Object> submission(Long id) {
        Map<String, Object> header = one("SELECT s.*,m.measure_code,m.period_id FROM owner_measurement_submission s JOIN production_measurement m ON m.id=s.measurement_id WHERE s.id=? AND s.tenant_id=? AND s.deleted_flag=0", id, tenant());
        if (header == null) throw error("OWNER_MEASUREMENT_SUBMISSION_NOT_FOUND", "业主报量版本不存在");
        projectAccessChecker.checkAccess(longValue(header.get("project_id")), "查看业主报量");
        Map<String, Object> result = new LinkedHashMap<>(header);
        result.put("lines", jdbc.queryForList("SELECT r.*,l.item_code,l.item_name,l.unit,l.unit_price FROM owner_measurement_review_line r JOIN production_measurement_line l ON l.id=r.measurement_line_id WHERE r.tenant_id=? AND r.submission_id=? ORDER BY l.sort_order,l.id", tenant(), id));
        Map<String, Object> settlement = one("SELECT * FROM owner_settlement WHERE tenant_id=? AND owner_submission_id=? AND deleted_flag=0", tenant(), id);
        result.put("settlement", settlement);
        return moneyPayload(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> review(Long submissionId, Integer version, OwnerReviewRequest request) {
        Map<String, Object> submission = requireSubmission(submissionId, true);
        String decision = request.decision().trim().toUpperCase(Locale.ROOT);
        if ("SETTLEMENT_CREATED".equals(submission.get("status")) && "CONFIRMED".equals(decision)) return submission(submissionId);
        if ("RETURNED".equals(submission.get("status")) && "RETURNED".equals(decision)) return submission(submissionId);
        requireVersion(version, submission, "OWNER_MEASUREMENT_SUBMISSION");
        if (!"SUBMITTED".equals(submission.get("status"))) throw error("OWNER_REVIEW_STATE_INVALID", "只有已报送版本可以登记业主核定");
        if ("RETURNED".equals(decision)) {
            if (request.reviewComment() == null || request.reviewComment().isBlank()) throw error("OWNER_RETURN_REASON_REQUIRED", "业主退回必须填写原因");
            int returned = jdbc.update("UPDATE owner_measurement_submission SET status='RETURNED',reviewer_name=?,review_comment=?,reviewed_at=CURRENT_TIMESTAMP,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND version=?", request.reviewerName().trim(), request.reviewComment().trim(), user(), submissionId, tenant(), version);
            if (returned != 1) throw error("OWNER_MEASUREMENT_SUBMISSION_CONCURRENT_UPDATE", "业主报量版本冲突，请刷新后重试");
            jdbc.update("UPDATE production_measurement SET status='OWNER_RETURNED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", user(), submission.get("measurement_id"), tenant());
            return submission(submissionId);
        }
        if (!"CONFIRMED".equals(decision)) throw error("OWNER_REVIEW_DECISION_INVALID", "业主核定结论只能为 CONFIRMED 或 RETURNED");
        validateSettlementFields(request);
        int attachmentCount = cleanFileCount("OWNER_MEASUREMENT_SUBMISSION", submissionId, "OWNER_CONFIRMATION");
        if (attachmentCount < 1) throw error("OWNER_CONFIRMATION_ATTACHMENT_REQUIRED", "业主核定与结算必须上传真实且扫描通过的核定附件");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.*,l.unit_price FROM owner_measurement_review_line r JOIN production_measurement_line l ON l.id=r.measurement_line_id WHERE r.tenant_id=? AND r.submission_id=? ORDER BY r.id FOR UPDATE", tenant(), submissionId);
        if (request.lines() == null || request.lines().size() != rows.size()) throw error("OWNER_REVIEW_LINE_INCOMPLETE", "必须逐项核定全部报量明细");
        Map<Long, OwnerReviewLineRequest> requested = new LinkedHashMap<>();
        for (OwnerReviewLineRequest line : request.lines()) {
            if (requested.put(line.measurementLineId(), line) != null) throw error("OWNER_REVIEW_LINE_DUPLICATE", "同一报量明细不能重复核定");
        }
        BigDecimal confirmedTotal = money(BigDecimal.ZERO);
        BigDecimal deductedTotal = money(BigDecimal.ZERO);
        for (Map<String, Object> row : rows) {
            Long lineId = longValue(row.get("measurement_line_id"));
            OwnerReviewLineRequest input = requested.remove(lineId);
            if (input == null) throw error("OWNER_REVIEW_LINE_MISMATCH", "核定明细与报量版本不一致");
            BigDecimal submittedQty = quantity(row.get("submitted_quantity"));
            BigDecimal confirmedQty = quantity(input.confirmedQuantity());
            if (confirmedQty.compareTo(submittedQty) > 0) throw error("OWNER_CONFIRMED_QUANTITY_EXCEEDED", "业主核定量不能超过本次报量");
            BigDecimal unitPrice = price(row.get("unit_price"));
            BigDecimal confirmedAmount = money(confirmedQty.multiply(unitPrice));
            BigDecimal deducted = money(decimal(row.get("submitted_amount")).subtract(confirmedAmount));
            if (deducted.signum() > 0 && (input.deductionReason() == null || input.deductionReason().isBlank())) {
                throw error("OWNER_DEDUCTION_REASON_REQUIRED", "存在核减时必须逐项填写核减原因");
            }
            jdbc.update("UPDATE owner_measurement_review_line SET confirmed_quantity=?,confirmed_amount=?,deducted_amount=?,deduction_reason=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", confirmedQty, confirmedAmount, deducted, blankToNull(input.deductionReason()), user(), row.get("id"), tenant());
            confirmedTotal = confirmedTotal.add(confirmedAmount); deductedTotal = deductedTotal.add(deducted);
        }
        if (!requested.isEmpty()) throw error("OWNER_REVIEW_LINE_MISMATCH", "核定包含不属于报量版本的明细");
        if (confirmedTotal.signum() <= 0) throw error("OWNER_CONFIRMED_AMOUNT_REQUIRED", "业主核定总额必须大于零");
        if (money(request.taxAmount()).compareTo(confirmedTotal) > 0) throw error("OWNER_SETTLEMENT_TAX_EXCEEDED", "税额不能超过业主核定金额");
        if (money(request.retentionAmount()).compareTo(confirmedTotal) > 0) throw error("OWNER_SETTLEMENT_RETENTION_EXCEEDED", "保留金不能超过业主核定金额");
        int confirmed = jdbc.update("UPDATE owner_measurement_submission SET confirmed_amount=?,deducted_amount=?,status='CONFIRMED',reviewer_name=?,review_comment=?,reviewed_at=CURRENT_TIMESTAMP,attachment_count=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND version=?", confirmedTotal, deductedTotal, request.reviewerName().trim(), blankToNull(request.reviewComment()), attachmentCount, user(), submissionId, tenant(), version);
        if (confirmed != 1) throw error("OWNER_MEASUREMENT_SUBMISSION_CONCURRENT_UPDATE", "业主报量版本冲突，请刷新后重试");

        Map<String, Object> contract = requireMainContract(longValue(submission.get("project_id")), longValue(submission.get("contract_id")));
        Map<String, Object> period = one("SELECT p.* FROM measurement_period p JOIN production_measurement m ON m.period_id=p.id WHERE m.id=? AND m.tenant_id=?", submission.get("measurement_id"), tenant());
        OwnerSettlementRequest settlementRequest = new OwnerSettlementRequest(
                longValue(submission.get("project_id")), longValue(submission.get("contract_id")), null,
                string(period.get("period_code")), request.settlementDate(), confirmedTotal, money(request.taxAmount()),
                money(request.retentionAmount()), request.dueDate(), longValue(contract.get("party_a_id")), attachmentCount,
                "由业主报量核定自动生成，报量版本=" + submission.get("submission_code"));
        Map<String, Object> settlement = revenueOperationsService.createSettlement(settlementRequest);
        Long settlementId = longValue(settlement.get("id"));
        jdbc.update("UPDATE owner_settlement SET production_measurement_id=?,owner_submission_id=?,reported_amount=?,deducted_amount=?,formula_version='OWNER_CONFIRMED_MEASUREMENT_V1',updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                submission.get("measurement_id"), submissionId, submission.get("submitted_amount"), deductedTotal, user(), settlementId, tenant());
        jdbc.update("UPDATE owner_measurement_submission SET status='SETTLEMENT_CREATED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", submissionId, tenant());
        jdbc.update("UPDATE production_measurement SET status='SETTLEMENT_CREATED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", user(), submission.get("measurement_id"), tenant());
        return submission(submissionId);
    }

    public Map<String, Object> traceBySettlement(Long settlementId) {
        Map<String, Object> settlement = one("SELECT * FROM owner_settlement WHERE id=? AND tenant_id=? AND deleted_flag=0", settlementId, tenant());
        if (settlement == null || settlement.get("production_measurement_id") == null) throw error("MEASUREMENT_TRACE_NOT_FOUND", "业主结算不存在或不是由产值计量生成");
        projectAccessChecker.checkAccess(longValue(settlement.get("project_id")), "查看产值计量全链路");
        Long measurementId = longValue(settlement.get("production_measurement_id"));
        Long submissionId = longValue(settlement.get("owner_submission_id"));
        Map<String, Object> measurement = one("SELECT * FROM production_measurement WHERE id=? AND tenant_id=?", measurementId, tenant());
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("project", one("SELECT * FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0", settlement.get("project_id"), tenant()));
        trace.put("contract", one("SELECT * FROM ct_contract WHERE id=? AND tenant_id=? AND deleted_flag=0", settlement.get("contract_id"), tenant()));
        trace.put("period", one("SELECT * FROM measurement_period WHERE id=? AND tenant_id=?", measurement.get("period_id"), tenant()));
        trace.put("measurement", measurement);
        trace.put("measurementLines", jdbc.queryForList("SELECT * FROM production_measurement_line WHERE tenant_id=? AND measurement_id=? ORDER BY sort_order,id", tenant(), measurementId));
        trace.put("ownerSubmission", one("SELECT * FROM owner_measurement_submission WHERE id=? AND tenant_id=?", submissionId, tenant()));
        trace.put("ownerReviewLines", jdbc.queryForList("SELECT * FROM owner_measurement_review_line WHERE tenant_id=? AND submission_id=? ORDER BY id", tenant(), submissionId));
        trace.put("settlement", settlement);
        trace.put("receivables", jdbc.queryForList("SELECT * FROM account_receivable WHERE tenant_id=? AND settlement_id=? AND deleted_flag=0 ORDER BY id", tenant(), settlementId));
        Set<Long> approvalIds = new LinkedHashSet<>();
        if (longValue(measurement.get("approval_instance_id")) != null) approvalIds.add(longValue(measurement.get("approval_instance_id")));
        if (longValue(settlement.get("approval_instance_id")) != null) approvalIds.add(longValue(settlement.get("approval_instance_id")));
        trace.put("approvalInstances", approvalIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM wf_instance WHERE tenant_id=? AND id IN(" + placeholders(approvalIds.size()) + ")", args(tenant(), approvalIds)));
        trace.put("approvalRecords", approvalIds.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM wf_record WHERE tenant_id=? AND instance_id IN(" + placeholders(approvalIds.size()) + ") AND deleted_flag=0 ORDER BY created_at,id", args(tenant(), approvalIds)));
        return moneyPayload(trace);
    }

    private ResolvedLine resolveLine(Long contractId, MeasurementLineRequest request) {
        boolean item = request.contractItemId() != null;
        boolean change = request.contractChangeId() != null;
        if (item == change) throw error("MEASUREMENT_SOURCE_INVALID", "计量明细必须且只能选择合同清单项或已批准变更之一");
        if (item) {
            Map<String, Object> row = one("SELECT * FROM ct_contract_item WHERE id=? AND tenant_id=? AND contract_id=? AND deleted_flag=0 FOR UPDATE", request.contractItemId(), tenant(), contractId);
            if (row == null) throw error("CONTRACT_ITEM_NOT_MEASURABLE", "合同清单项不存在或不属于所选合同");
            BigDecimal contractQty = quantity(row.get("quantity"));
            BigDecimal prior = approvedQuantity("CONTRACT_ITEM", request.contractItemId(), null);
            validateQuantity(contractQty, prior, request.currentQuantity());
            BigDecimal unitPrice = price(row.get("unit_price"));
            return new ResolvedLine("CONTRACT_ITEM", request.contractItemId(), null, request.contractItemId(), string(row.get("item_code")), string(row.get("item_name")), string(row.get("item_spec")), string(row.get("unit")), contractQty, prior, quantity(request.currentQuantity()), unitPrice, money(request.currentQuantity().multiply(unitPrice)), request.evidenceCount());
        }
        Map<String, Object> row = one("SELECT * FROM ct_contract_change WHERE id=? AND tenant_id=? AND contract_id=? AND deleted_flag=0 AND approval_status='APPROVED' AND effective_flag=1 AND change_amount>0 FOR UPDATE", request.contractChangeId(), tenant(), contractId);
        if (row == null) throw error("CONTRACT_CHANGE_NOT_MEASURABLE", "合同变更不存在、未生效、为负变更或不属于所选合同");
        BigDecimal prior = approvedQuantity("CONTRACT_CHANGE", null, request.contractChangeId());
        validateQuantity(quantity(BigDecimal.ONE), prior, request.currentQuantity());
        BigDecimal unitPrice = price(row.get("change_amount"));
        return new ResolvedLine("CONTRACT_CHANGE", null, request.contractChangeId(), request.contractChangeId(), string(row.get("change_code")), string(row.get("change_name")), null, "项", quantity(BigDecimal.ONE), prior, quantity(request.currentQuantity()), unitPrice, money(request.currentQuantity().multiply(unitPrice)), request.evidenceCount());
    }

    private void validateQuantity(BigDecimal contractQty, BigDecimal prior, BigDecimal current) {
        BigDecimal quantity = quantity(current);
        if (contractQty.signum() <= 0 || quantity.signum() <= 0 || prior.add(quantity).compareTo(contractQty) > 0) {
            throw error("MEASUREMENT_QUANTITY_EXCEEDED", "本次计量量加累计已审批量不能超过合同清单或变更可计量量");
        }
    }

    private void revalidateCapacity(Long measurementId, Map<String, Object> line) {
        BigDecimal limit;
        BigDecimal prior;
        if (line.get("contract_item_id") != null) {
            Map<String, Object> source = one("SELECT quantity FROM ct_contract_item WHERE id=? AND tenant_id=? AND deleted_flag=0 FOR UPDATE", line.get("contract_item_id"), tenant());
            if (source == null) throw error("CONTRACT_ITEM_NOT_MEASURABLE", "合同清单项已不存在");
            limit = quantity(source.get("quantity"));
            prior = approvedQuantityExcluding("CONTRACT_ITEM", longValue(line.get("contract_item_id")), null, measurementId);
        } else {
            Map<String, Object> source = one("SELECT change_amount FROM ct_contract_change WHERE id=? AND tenant_id=? AND deleted_flag=0 AND approval_status='APPROVED' AND effective_flag=1 AND change_amount>0 FOR UPDATE", line.get("contract_change_id"), tenant());
            if (source == null) throw error("CONTRACT_CHANGE_NOT_MEASURABLE", "合同变更已失效或不可计量");
            limit = quantity(BigDecimal.ONE);
            prior = approvedQuantityExcluding("CONTRACT_CHANGE", null, longValue(line.get("contract_change_id")), measurementId);
        }
        validateQuantity(limit, prior, decimal(line.get("current_reported_quantity")));
    }

    private BigDecimal approvedQuantity(String type, Long itemId, Long changeId) { return approvedQuantityExcluding(type, itemId, changeId, null); }
    private BigDecimal approvedQuantityExcluding(String type, Long itemId, Long changeId, Long excludedMeasurementId) {
        String column = "CONTRACT_ITEM".equals(type) ? "l.contract_item_id" : "l.contract_change_id";
        Long sourceId = "CONTRACT_ITEM".equals(type) ? itemId : changeId;
        String excluded = excludedMeasurementId == null ? "" : " AND m.id<>?";
        List<Object> values = new ArrayList<>(List.of(tenant(), sourceId));
        if (excludedMeasurementId != null) values.add(excludedMeasurementId);
        return quantity(jdbc.queryForObject("SELECT COALESCE(SUM(l.current_reported_quantity),0) FROM production_measurement_line l JOIN production_measurement m ON m.id=l.measurement_id WHERE l.tenant_id=? AND " + column + "=? AND m.deleted_flag=0 AND m.status IN('INTERNAL_APPROVED','OWNER_SUBMITTED','OWNER_RETURNED','OWNER_CONFIRMED','SETTLEMENT_CREATED')" + excluded, BigDecimal.class, values.toArray()));
    }

    private Map<String, Object> requireMainContract(Long projectId, Long contractId) {
        projectAccessChecker.checkAccess(projectId, "办理产值计量");
        Map<String, Object> row = one("""
                SELECT c.id,c.project_id,c.party_a_id,c.contract_type,c.contract_status,c.approval_status,c.current_amount,p.status project_status
                  FROM ct_contract c JOIN pm_project p ON p.id=c.project_id AND p.tenant_id=c.tenant_id AND p.deleted_flag=0
                 WHERE c.id=? AND c.tenant_id=? AND c.deleted_flag=0
                """, contractId, tenant());
        if (row == null || !Objects.equals(longValue(row.get("project_id")), projectId)) throw error("MEASUREMENT_CONTRACT_PROJECT_MISMATCH", "业主合同不属于所选项目");
        if (!"ACTIVE".equals(row.get("project_status"))) throw error("MEASUREMENT_PROJECT_NOT_ACTIVE", "只有 ACTIVE 项目可以办理产值计量");
        if (!"MAIN".equals(row.get("contract_type")) || !"APPROVED".equals(row.get("approval_status")) || !"PERFORMING".equals(row.get("contract_status"))) throw error("MEASUREMENT_CONTRACT_NOT_PERFORMING", "只有已审批且履约中的 MAIN 业主合同可以办理产值计量");
        return row;
    }

    private Map<String, Object> period(Long id) {
        Map<String, Object> row = one("SELECT * FROM measurement_period WHERE id=? AND tenant_id=? AND deleted_flag=0", id, tenant());
        if (row == null) throw error("MEASUREMENT_PERIOD_NOT_FOUND", "计量周期不存在");
        return row;
    }
    private Map<String, Object> requirePeriod(Long id, boolean access) {
        Map<String, Object> row = one("SELECT * FROM measurement_period WHERE id=? AND tenant_id=? AND deleted_flag=0" + (access ? " FOR UPDATE" : ""), id, tenant());
        if (row == null) throw error("MEASUREMENT_PERIOD_NOT_FOUND", "计量周期不存在");
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "办理计量周期"); return row;
    }
    private Map<String, Object> requireMeasurement(Long id, boolean lock) {
        Map<String, Object> row = one("SELECT * FROM production_measurement WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""), id, tenant());
        if (row == null) throw error("PRODUCTION_MEASUREMENT_NOT_FOUND", "产值计量单不存在");
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "办理产值计量"); return row;
    }
    private Map<String, Object> requireSubmission(Long id, boolean lock) {
        Map<String, Object> row = one("SELECT * FROM owner_measurement_submission WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""), id, tenant());
        if (row == null) throw error("OWNER_MEASUREMENT_SUBMISSION_NOT_FOUND", "业主报量版本不存在");
        projectAccessChecker.checkAccess(longValue(row.get("project_id")), "办理业主核定"); return row;
    }
    private void validateSettlementFields(OwnerReviewRequest request) {
        if (request.settlementDate() == null || request.dueDate() == null || request.taxAmount() == null || request.retentionAmount() == null || request.attachmentCount() == null) throw error("OWNER_SETTLEMENT_FIELDS_REQUIRED", "确认业主核定时必须填写结算日、到期日、税额、保留金和附件数量");
        if (request.dueDate().isBefore(request.settlementDate())) throw error("OWNER_SETTLEMENT_DUE_DATE_INVALID", "应收到期日不能早于结算日期");
    }
    private void validateDateWindow(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw error("MEASUREMENT_REPORT_DATE_INVALID", "计量报表开始日期不能晚于结束日期");
        }
    }
    private int cleanFileCount(String businessType, Long businessId, String documentType) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_file WHERE tenant_id=? AND business_type=? AND business_id=? AND document_type=? AND virus_scan_status='CLEAN' AND deleted_flag=0",
                Integer.class, tenant(), businessType, businessId, documentType);
        return count == null ? 0 : count;
    }
    private void requireVersion(Integer expected, Map<String, Object> row, String prefix) {
        if (expected == null || expected < 0) throw error(prefix + "_VERSION_REQUIRED", "客户端版本不能为空且必须大于等于0");
        if (expected != intValue(row.get("version"))) throw error(prefix + "_CONCURRENT_UPDATE", "数据版本冲突，请刷新后重试");
    }
    private Map<String, Object> one(String sql, Object... args) { try { return jdbc.queryForMap(sql, args); } catch (EmptyResultDataAccessException e) { return null; } }
    private Long tenant() { return UserContext.getCurrentTenantId(); }
    private Long user() { return UserContext.getCurrentUserId(); }
    private Long longValue(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private int intValue(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private String string(Object value) { return value == null ? null : value.toString(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String nextMeasurementCode(LocalDate measureDate, int offset) {
        String prefix = "PM-" + measureDate.format(MONTH_CODE) + "-";
        String last = jdbc.query("SELECT measure_code FROM production_measurement WHERE tenant_id=? AND measure_code LIKE ? AND deleted_flag=0 ORDER BY measure_code DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, tenant(), prefix + "%");
        int sequence = 1 + offset;
        if (last != null) {
            try { sequence = Integer.parseInt(last.substring(prefix.length())) + 1 + offset; }
            catch (RuntimeException ignored) { /* 非标准历史编号不阻止新规则编号生成。 */ }
        }
        if (sequence > 999) throw error("PRODUCTION_MEASUREMENT_MONTHLY_LIMIT", "当月计量编号已用尽");
        return prefix + String.format("%03d", sequence);
    }
    private boolean duplicateOf(DuplicateKeyException error, String constraint) {
        return String.valueOf(error.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT).contains(constraint);
    }
    private BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    private BigDecimal money(Object value) { return decimal(value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal quantity(Object value) { return decimal(value).setScale(4, RoundingMode.HALF_UP); }
    private BigDecimal price(Object value) { return decimal(value).setScale(4, RoundingMode.HALF_UP); }
    private java.time.LocalDate localDate(Object value) { if (value instanceof java.time.LocalDate d) return d; if (value instanceof java.sql.Date d) return d.toLocalDate(); return java.time.LocalDate.parse(value.toString()); }
    private BusinessException error(String code, String message) { return new BusinessException(code, message); }
    private List<Long> projectIdsForQuery(Long projectId, String permission) {
        if (projectId != null) {
            projectAccessChecker.checkAccess(projectId, permission);
            return List.of(projectId);
        }
        return projectAccessChecker.accessibleProjectIds();
    }
    private String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
    private Object[] args(Object first, Collection<?> rest) { List<Object> values = new ArrayList<>(); values.add(first); values.addAll(rest); return values.toArray(); }

    @SuppressWarnings("unchecked")
    private static <T> T moneyPayload(T value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), moneyEntry(String.valueOf(key), item)));
            return (T) normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) normalized.add(moneyPayload(item));
            return (T) normalized;
        }
        return value;
    }

    private static Object moneyEntry(String key, Object value) {
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        return moneyPayload(value);
    }

    private record ResolvedLine(String sourceType, Long contractItemId, Long contractChangeId, Long sourceId,
                                String itemCode, String itemName, String itemSpec, String unit,
                                BigDecimal contractQuantity, BigDecimal priorQuantity, BigDecimal currentQuantity,
                                BigDecimal unitPrice, BigDecimal currentAmount, Integer evidenceCount) {}
}
