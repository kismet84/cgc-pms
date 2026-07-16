package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.dto.CostControlModels.CorrectiveActionRequest;
import com.cgcpms.cost.dto.CostControlModels.CorrectiveCloseRequest;
import com.cgcpms.cost.dto.CostControlModels.ForecastItemRequest;
import com.cgcpms.cost.dto.CostControlModels.ForecastRequest;
import com.cgcpms.project.auth.ProjectAccessChecker;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CostControlService {
    private final JdbcTemplate jdbc;
    private final WorkflowEngine workflowEngine;
    private final ProjectAccessChecker projectAccessChecker;
    private final CostSummaryService costSummaryService;

    public Map<String, Object> overview(Long projectId) {
        requireProject(projectId, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", queryOne("SELECT id,project_code,project_name,status,contract_amount,target_cost FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "PROJECT_NOT_FOUND", "项目不存在", projectId, tenant()));
        Map<String, Object> target = findActiveTarget(projectId, false);
        result.put("activeTarget", target);
        result.put("targetItems", target.isEmpty() ? List.of() : targetItems(id(target)));
        result.put("forecastInputItems", target.isEmpty() ? List.of() : forecastInputItems(projectId, id(target), LocalDate.now()));
        Map<String, Object> forecast = findLatestForecast(projectId);
        result.put("latestForecast", forecast);
        result.put("forecastItems", forecast.isEmpty() ? List.of() : forecastItems(id(forecast)));
        result.put("correctiveActions", forecast.isEmpty() ? List.of() : corrections(id(forecast)));
        result.put("forecastHistory", jdbc.queryForList("SELECT * FROM cost_forecast WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY version_no DESC", tenant(), projectId));
        result.put("costSources", costSourceBreakdown(projectId, LocalDate.now()));
        result.put("summary", latestSummary(projectId));
        return result;
    }

    public Map<String, Object> trace(Long forecastId) {
        Map<String, Object> forecast = requireForecast(forecastId, false);
        Long projectId = idValue(forecast.get("project_id"));
        requireProject(projectId, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", queryOne("SELECT id,project_code,project_name,status FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "PROJECT_NOT_FOUND", "项目不存在", projectId, tenant()));
        result.put("target", queryOne("SELECT * FROM cost_target WHERE id=? AND tenant_id=? AND deleted_flag=0",
                "COST_TARGET_NOT_FOUND", "目标成本不存在", forecast.get("cost_target_id"), tenant()));
        result.put("targetItems", targetItems(idValue(forecast.get("cost_target_id"))));
        result.put("forecast", forecast);
        result.put("forecastItems", forecastItems(forecastId));
        List<Map<String, Object>> actions = corrections(forecastId);
        result.put("correctiveActions", actions);
        result.put("approvalInstances", jdbc.queryForList("SELECT w.* FROM wf_instance w JOIN cost_corrective_action c ON c.approval_instance_id=w.id WHERE c.tenant_id=? AND c.forecast_id=? AND c.deleted_flag=0 ORDER BY w.started_at", tenant(), forecastId));
        result.put("costSources", costSourceBreakdown(projectId, localDate(forecast.get("forecast_date"))));
        result.put("summary", jdbc.queryForList("SELECT * FROM cost_summary WHERE tenant_id=? AND project_id=? AND cost_forecast_id=? AND deleted_flag=0 ORDER BY summary_date DESC,cost_subject_id", tenant(), projectId, forecastId));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createForecast(ForecastRequest request) {
        requireProject(request.projectId(), true);
        Map<String, Object> activeTarget = requireActiveTarget(request.projectId(), false);
        ensureNoOpenPriorVariance(request.projectId(), null);
        Integer versionNo = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM cost_forecast WHERE tenant_id=? AND project_id=? AND deleted_flag=0", Integer.class, tenant(), request.projectId());
        long forecastId = IdWorker.getId();
        persistForecast(forecastId, activeTarget, versionNo == null ? 1 : versionNo, request, true);
        return requireForecast(forecastId, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateForecast(Long forecastId, ForecastRequest request) {
        Map<String, Object> existing = requireForecast(forecastId, true);
        if (!"DRAFT".equals(string(existing.get("status")))) throw error("COST_FORECAST_IMMUTABLE", "仅草稿预测可编辑");
        if (!Objects.equals(idValue(existing.get("project_id")), request.projectId())) throw error("COST_FORECAST_PROJECT_IMMUTABLE", "预测所属项目不可修改");
        requireProject(request.projectId(), true);
        Map<String, Object> activeTarget = requireActiveTarget(request.projectId(), false);
        if (!Objects.equals(idValue(existing.get("cost_target_id")), id(activeTarget))) throw error("COST_FORECAST_TARGET_CHANGED", "目标成本版本已切换，请重新创建预测");
        persistForecast(forecastId, activeTarget, intValue(existing.get("version_no")), request, false);
        return requireForecast(forecastId, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmForecast(Long forecastId) {
        Map<String, Object> forecast = requireForecast(forecastId, true);
        if (!"DRAFT".equals(string(forecast.get("status")))) throw error("COST_FORECAST_ALREADY_CONFIRMED", "完工预测已确认，不可重复确认");
        Long projectId = idValue(forecast.get("project_id"));
        requireProject(projectId, true);
        ensureNoOpenPriorVariance(projectId, forecastId);
        Map<String, Object> activeTarget = requireActiveTarget(projectId, true);
        if (!Objects.equals(idValue(forecast.get("cost_target_id")), id(activeTarget))) throw error("COST_FORECAST_TARGET_CHANGED", "目标成本版本已切换，请重新创建预测");

        ForecastRequest refreshed = new ForecastRequest(projectId, string(forecast.get("forecast_code")), string(forecast.get("forecast_name")),
                localDate(forecast.get("forecast_date")), forecastItems(forecastId).stream().map(row -> new ForecastItemRequest(
                        idValue(row.get("cost_subject_id")), money(row.get("estimated_remaining_amount")), stringNullable(row.get("remark")))).toList(),
                stringNullable(forecast.get("remark")));
        persistForecast(forecastId, activeTarget, intValue(forecast.get("version_no")), refreshed, false);
        forecast = requireForecast(forecastId, true);
        String status = money(forecast.get("cost_variance_amount")).compareTo(BigDecimal.ZERO) > 0 ? "ACTION_REQUIRED" : "CONTROLLED";
        jdbc.update("UPDATE cost_forecast SET status='SUPERSEDED',updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND project_id=? AND id<>? AND status IN('ACTION_REQUIRED','CONTROLLED') AND deleted_flag=0", user(), tenant(), projectId, forecastId);
        jdbc.update("UPDATE cost_forecast SET status=?,confirmed_at=CURRENT_TIMESTAMP,confirmed_by=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='DRAFT'", status, user(), user(), forecastId, tenant());

        costSummaryService.refreshSummary(tenant(), projectId);
        Map<String, Object> confirmed = requireForecast(forecastId, false);
        jdbc.update("UPDATE cost_summary SET cost_forecast_id=?,responsibility_cost=?,forecast_at_completion_cost=?,forecast_profit=?,profit_margin=? WHERE tenant_id=? AND project_id=? AND summary_date=? AND deleted_flag=0",
                forecastId, confirmed.get("responsibility_amount"), confirmed.get("forecast_at_completion_amount"), confirmed.get("forecast_profit_amount"), confirmed.get("profit_margin"), tenant(), projectId, LocalDate.now());
        return confirmed;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createCorrectiveAction(CorrectiveActionRequest request) {
        Map<String, Object> forecast = requireForecast(request.forecastId(), true);
        if (!"ACTION_REQUIRED".equals(string(forecast.get("status")))) throw error("COST_CORRECTIVE_FORECAST_INVALID", "仅存在正偏差且待纠偏的已确认预测可建立纠偏措施");
        Long projectId = idValue(forecast.get("project_id"));
        requireProject(projectId, true);
        BigDecimal expected = money(request.expectedSavingAmount());
        BigDecimal allocated = jdbc.queryForObject("SELECT COALESCE(SUM(expected_saving_amount),0) FROM cost_corrective_action WHERE tenant_id=? AND forecast_id=? AND deleted_flag=0 AND status<>'CANCELLED'", BigDecimal.class, tenant(), request.forecastId());
        if (money(allocated).add(expected).compareTo(money(forecast.get("cost_variance_amount"))) > 0) {
            throw error("COST_CORRECTIVE_SAVING_EXCEEDS_VARIANCE", "纠偏预计节约金额合计不能超过预测成本偏差");
        }
        requireEnabledUser(request.responsibleUserId());
        long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO cost_corrective_action(id,tenant_id,project_id,forecast_id,action_code,action_title,root_cause,action_plan,
                    expected_saving_amount,responsible_user_id,due_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                    """, id, tenant(), projectId, request.forecastId(), request.actionCode().trim(), request.actionTitle().trim(),
                    request.rootCause().trim(), request.actionPlan().trim(), expected, request.responsibleUserId(), request.dueDate(), user(), user(), request.remark());
        } catch (DuplicateKeyException e) {
            throw error("COST_CORRECTIVE_CODE_DUPLICATE", "纠偏措施编号已存在");
        }
        return requireCorrection(id, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateCorrectiveAction(Long id, CorrectiveActionRequest request) {
        Map<String, Object> action = requireCorrection(id, true);
        if (!Set.of("DRAFT", "REJECTED").contains(string(action.get("status")))) throw error("COST_CORRECTIVE_IMMUTABLE", "仅草稿或驳回纠偏措施可编辑");
        if (!Objects.equals(idValue(action.get("forecast_id")), request.forecastId())) throw error("COST_CORRECTIVE_FORECAST_IMMUTABLE", "纠偏措施所属预测不可修改");
        requireEnabledUser(request.responsibleUserId());
        BigDecimal allocated = jdbc.queryForObject("SELECT COALESCE(SUM(expected_saving_amount),0) FROM cost_corrective_action WHERE tenant_id=? AND forecast_id=? AND id<>? AND deleted_flag=0 AND status<>'CANCELLED'", BigDecimal.class, tenant(), request.forecastId(), id);
        BigDecimal variance = money(requireForecast(request.forecastId(), false).get("cost_variance_amount"));
        if (money(allocated).add(money(request.expectedSavingAmount())).compareTo(variance) > 0) throw error("COST_CORRECTIVE_SAVING_EXCEEDS_VARIANCE", "纠偏预计节约金额合计不能超过预测成本偏差");
        try {
            jdbc.update("UPDATE cost_corrective_action SET action_code=?,action_title=?,root_cause=?,action_plan=?,expected_saving_amount=?,responsible_user_id=?,due_date=?,status='DRAFT',remark=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                    request.actionCode().trim(), request.actionTitle().trim(), request.rootCause().trim(), request.actionPlan().trim(), money(request.expectedSavingAmount()), request.responsibleUserId(), request.dueDate(), request.remark(), user(), id, tenant());
        } catch (DuplicateKeyException e) {
            throw error("COST_CORRECTIVE_CODE_DUPLICATE", "纠偏措施编号已存在");
        }
        return requireCorrection(id, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitCorrectiveAction(Long id) {
        Map<String, Object> action = requireCorrection(id, true);
        String status = string(action.get("status"));
        if (!Set.of("DRAFT", "REJECTED").contains(status)) throw error("COST_CORRECTIVE_NOT_SUBMITTABLE", "仅草稿或驳回纠偏措施可提交");
        requireProject(idValue(action.get("project_id")), true);
        WfInstance instance;
        Long existing = idValueNullable(action.get("approval_instance_id"));
        if ("REJECTED".equals(status) && existing != null) {
            instance = workflowEngine.resubmit(existing, user(), UserContext.getCurrentUsername());
        } else {
            instance = workflowEngine.submit(user(), UserContext.getCurrentUsername(), tenant(), WorkflowBusinessTypes.COST_CORRECTIVE_ACTION,
                    id, string(action.get("action_title")), money(action.get("expected_saving_amount")), idValue(action.get("project_id")), null,
                    string(action.get("root_cause")), null, null);
        }
        jdbc.update("UPDATE cost_corrective_action SET status='PENDING',approval_instance_id=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", instance.getId(), user(), id, tenant());
        return requireCorrection(id, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> closeCorrectiveAction(Long id, CorrectiveCloseRequest request) {
        Map<String, Object> action = requireCorrection(id, true);
        if (!"APPROVED".equals(string(action.get("status")))) throw error("COST_CORRECTIVE_NOT_CLOSABLE", "仅审批通过的纠偏措施可关闭");
        requireProject(idValue(action.get("project_id")), true);
        jdbc.update("UPDATE cost_corrective_action SET status='CLOSED',actual_saving_amount=?,result_description=?,completed_at=CURRENT_TIMESTAMP,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='APPROVED'",
                money(request.actualSavingAmount()), request.resultDescription().trim(), user(), id, tenant());
        Long forecastId = idValue(action.get("forecast_id"));
        Integer remaining = jdbc.queryForObject("SELECT COUNT(*) FROM cost_corrective_action WHERE tenant_id=? AND forecast_id=? AND deleted_flag=0 AND status NOT IN('CLOSED','CANCELLED')", Integer.class, tenant(), forecastId);
        if (remaining != null && remaining == 0) jdbc.update("UPDATE cost_forecast SET status='CONTROLLED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='ACTION_REQUIRED'", user(), forecastId, tenant());
        return requireCorrection(id, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void onCorrectiveApproved(Long id) {
        jdbc.update("UPDATE cost_corrective_action SET status='APPROVED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant());
    }

    @Transactional(rollbackFor = Exception.class)
    public void onCorrectiveRejected(Long id) {
        jdbc.update("UPDATE cost_corrective_action SET status='REJECTED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PENDING'", id, tenant());
    }

    private void persistForecast(long forecastId, Map<String, Object> target, int versionNo, ForecastRequest request, boolean insert) {
        List<Map<String, Object>> targetItems = targetItems(id(target));
        if (targetItems.isEmpty()) throw error("COST_FORECAST_TARGET_ITEMS_REQUIRED", "生效目标成本缺少科目责任预算");
        Map<Long, ForecastItemRequest> requested = new LinkedHashMap<>();
        for (ForecastItemRequest item : request.items()) {
            if (requested.put(item.costSubjectId(), item) != null) throw error("COST_FORECAST_SUBJECT_DUPLICATE", "完工预测成本科目不能重复");
        }
        Map<Long, Map<String, Object>> sources = costSourcesBySubject(request.projectId(), request.forecastDate());
        Set<Long> requiredSubjects = new HashSet<>(sources.keySet());
        for (Map<String, Object> item : targetItems) requiredSubjects.add(idValue(item.get("cost_subject_id")));
        if (!requested.keySet().equals(requiredSubjects)) throw error("COST_FORECAST_SUBJECT_INCOMPLETE", "完工预测必须完整覆盖目标成本和已发生成本科目");

        Map<Long, Map<String, Object>> targetBySubject = new HashMap<>();
        for (Map<String, Object> item : targetItems) targetBySubject.put(idValue(item.get("cost_subject_id")), item);
        BigDecimal totalBid = BigDecimal.ZERO, totalTarget = BigDecimal.ZERO, totalResponsibility = BigDecimal.ZERO;
        BigDecimal totalCommitted = BigDecimal.ZERO, totalActual = BigDecimal.ZERO, totalRemaining = BigDecimal.ZERO;
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Long subjectId : requiredSubjects) {
            Map<String, Object> targetItem = targetBySubject.getOrDefault(subjectId, Map.of());
            Map<String, Object> source = sources.getOrDefault(subjectId, Map.of());
            BigDecimal bid = money(targetItem.get("bid_cost_amount"));
            BigDecimal targetAmount = money(targetItem.get("target_amount"));
            BigDecimal responsibility = money(targetItem.get("responsibility_amount"));
            BigDecimal committed = money(source.get("committed_amount"));
            BigDecimal actual = money(source.get("actual_amount"));
            BigDecimal remaining = money(requested.get(subjectId).estimatedRemainingAmount());
            BigDecimal completion = actual.add(remaining);
            BigDecimal variance = completion.subtract(responsibility);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subjectId", subjectId); row.put("bid", bid); row.put("target", targetAmount); row.put("responsibility", responsibility);
            row.put("committed", committed); row.put("actual", actual); row.put("remaining", remaining); row.put("completion", completion); row.put("variance", variance);
            row.put("responsibleUserId", targetItem.get("responsible_user_id")); row.put("responsibilityUnit", targetItem.get("responsibility_unit")); row.put("remark", requested.get(subjectId).remark());
            snapshots.add(row);
            totalBid = totalBid.add(bid); totalTarget = totalTarget.add(targetAmount); totalResponsibility = totalResponsibility.add(responsibility);
            totalCommitted = totalCommitted.add(committed); totalActual = totalActual.add(actual); totalRemaining = totalRemaining.add(remaining);
        }
        BigDecimal completion = totalActual.add(totalRemaining);
        BigDecimal income = contractIncome(request.projectId());
        BigDecimal profit = income.subtract(completion);
        BigDecimal variance = completion.subtract(totalResponsibility);
        BigDecimal margin = income.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO.setScale(6) : profit.divide(income, 6, RoundingMode.HALF_UP);
        try {
            if (insert) {
                jdbc.update("""
                        INSERT INTO cost_forecast(id,tenant_id,project_id,cost_target_id,forecast_code,forecast_name,version_no,forecast_date,
                        bid_cost_amount,target_cost_amount,responsibility_amount,committed_cost_amount,actual_cost_amount,estimated_remaining_amount,
                        forecast_at_completion_amount,contract_income_amount,forecast_profit_amount,cost_variance_amount,profit_margin,status,formula_version,
                        version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT','COST_EAC_V1',0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0,?)
                        """, forecastId, tenant(), request.projectId(), id(target), request.forecastCode().trim(), request.forecastName().trim(), versionNo,
                        request.forecastDate(), totalBid, totalTarget, totalResponsibility, totalCommitted, totalActual, totalRemaining, completion, income, profit, variance, margin, user(), user(), request.remark());
            } else {
                jdbc.update("UPDATE cost_forecast SET forecast_code=?,forecast_name=?,forecast_date=?,bid_cost_amount=?,target_cost_amount=?,responsibility_amount=?,committed_cost_amount=?,actual_cost_amount=?,estimated_remaining_amount=?,forecast_at_completion_amount=?,contract_income_amount=?,forecast_profit_amount=?,cost_variance_amount=?,profit_margin=?,remark=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='DRAFT'",
                        request.forecastCode().trim(), request.forecastName().trim(), request.forecastDate(), totalBid, totalTarget, totalResponsibility, totalCommitted, totalActual, totalRemaining, completion, income, profit, variance, margin, request.remark(), user(), forecastId, tenant());
                jdbc.update("DELETE FROM cost_forecast_item WHERE tenant_id=? AND forecast_id=?", tenant(), forecastId);
            }
            for (Map<String, Object> row : snapshots) {
                jdbc.update("""
                        INSERT INTO cost_forecast_item(id,tenant_id,forecast_id,project_id,cost_subject_id,bid_cost_amount,target_cost_amount,
                        responsibility_amount,committed_cost_amount,actual_cost_amount,estimated_remaining_amount,forecast_at_completion_amount,
                        cost_variance_amount,responsible_user_id,responsibility_unit,created_by,created_at,remark)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)
                        """, IdWorker.getId(), tenant(), forecastId, request.projectId(), row.get("subjectId"), row.get("bid"), row.get("target"), row.get("responsibility"), row.get("committed"), row.get("actual"), row.get("remaining"), row.get("completion"), row.get("variance"), row.get("responsibleUserId"), row.get("responsibilityUnit"), user(), row.get("remark"));
            }
        } catch (DuplicateKeyException e) {
            throw error("COST_FORECAST_CODE_DUPLICATE", "完工预测编号或版本已存在");
        }
    }

    private Map<Long, Map<String, Object>> costSourcesBySubject(Long projectId, LocalDate date) {
        Integer unclassified = jdbc.queryForObject("SELECT COUNT(*) FROM cost_item WHERE tenant_id=? AND project_id=? AND deleted_flag=0 AND cost_subject_id IS NULL AND amount<>0 AND (cost_date IS NULL OR cost_date<=?)", Integer.class, tenant(), projectId, date);
        if (unclassified != null && unclassified > 0) throw error("COST_FORECAST_UNCLASSIFIED_COST", "项目存在未归类成本，无法生成可靠完工预测");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ci.cost_subject_id,
                SUM(CASE WHEN ci.source_type='CT_CONTRACT' AND ci.cost_status='CONFIRMED'
                    AND NOT EXISTS (SELECT 1 FROM ct_contract c WHERE c.id=ci.contract_id AND c.tenant_id=ci.tenant_id AND c.contract_type='MAIN' AND c.deleted_flag=0)
                    THEN ci.amount ELSE 0 END) committed_amount,
                SUM(CASE WHEN ci.source_type IN ('MAT_RECEIPT','MAT_REQUISITION','SUB_MEASURE','VAR_ORDER','CT_CHANGE','BID_COST','BID_COST_TRANSFERRED','OVERHEAD_ALLOCATION') AND ci.cost_status IN('CONFIRMED','POSTED') THEN ci.amount ELSE 0 END) actual_amount
                FROM cost_item ci WHERE ci.tenant_id=? AND ci.project_id=? AND ci.deleted_flag=0 AND ci.cost_subject_id IS NOT NULL AND (ci.cost_date IS NULL OR ci.cost_date<=?)
                GROUP BY ci.cost_subject_id
                HAVING committed_amount<>0 OR actual_amount<>0
                """, tenant(), projectId, date);
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) result.put(idValue(row.get("cost_subject_id")), row);
        return result;
    }

    private List<Map<String, Object>> costSourceBreakdown(Long projectId, LocalDate date) {
        return jdbc.queryForList("SELECT source_type,cost_status,COUNT(*) item_count,COALESCE(SUM(amount),0) amount FROM cost_item WHERE tenant_id=? AND project_id=? AND deleted_flag=0 AND (cost_date IS NULL OR cost_date<=?) GROUP BY source_type,cost_status ORDER BY source_type,cost_status", tenant(), projectId, date);
    }

    private BigDecimal contractIncome(Long projectId) {
        BigDecimal value = jdbc.queryForObject("SELECT COALESCE(SUM(COALESCE(current_amount,contract_amount,0)),0) FROM ct_contract WHERE tenant_id=? AND project_id=? AND contract_type='MAIN' AND approval_status='APPROVED' AND deleted_flag=0", BigDecimal.class, tenant(), projectId);
        return money(value);
    }

    private void ensureNoOpenPriorVariance(Long projectId, Long excludeForecastId) {
        String sql = "SELECT COUNT(*) FROM cost_forecast WHERE tenant_id=? AND project_id=? AND status='ACTION_REQUIRED' AND deleted_flag=0"
                + (excludeForecastId == null ? "" : " AND id<>?");
        Integer count = excludeForecastId == null
                ? jdbc.queryForObject(sql, Integer.class, tenant(), projectId)
                : jdbc.queryForObject(sql, Integer.class, tenant(), projectId, excludeForecastId);
        if (count != null && count > 0) {
            throw error("COST_FORECAST_PREVIOUS_ACTION_REQUIRED", "上一版正偏差纠偏尚未全部审批并关闭，不得创建或确认新预测");
        }
    }

    private Map<String, Object> requireProject(Long projectId, boolean writable) {
        projectAccessChecker.checkAccess(projectId, writable ? "维护目标成本与动态利润" : "查看目标成本与动态利润");
        Map<String, Object> project = queryOne("SELECT * FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0", "PROJECT_NOT_FOUND", "项目不存在", projectId, tenant());
        if (writable && !"ACTIVE".equals(string(project.get("status")))) throw error("PROJECT_NOT_ACTIVE", "只有进行中的项目可以维护成本预测和纠偏");
        return project;
    }

    private Map<String, Object> requireActiveTarget(Long projectId, boolean lock) {
        Map<String, Object> row = findActiveTarget(projectId, lock);
        if (row.isEmpty()) throw error("COST_ACTIVE_TARGET_REQUIRED", "项目尚无已审批生效的目标成本与责任预算");
        return row;
    }

    private Map<String, Object> findActiveTarget(Long projectId, boolean lock) {
        try {
            return jdbc.queryForMap("SELECT * FROM cost_target WHERE tenant_id=? AND project_id=? AND is_active=1 AND status='ACTIVE' AND approval_status='APPROVED' AND deleted_flag=0 ORDER BY effective_date DESC,id DESC LIMIT 1" + (lock ? " FOR UPDATE" : ""), tenant(), projectId);
        } catch (EmptyResultDataAccessException e) {
            return Map.of();
        }
    }

    private Map<String, Object> findLatestForecast(Long projectId) {
        try { return jdbc.queryForMap("SELECT * FROM cost_forecast WHERE tenant_id=? AND project_id=? AND deleted_flag=0 ORDER BY version_no DESC LIMIT 1", tenant(), projectId); }
        catch (EmptyResultDataAccessException e) { return Map.of(); }
    }

    private List<Map<String, Object>> targetItems(Long targetId) {
        return jdbc.queryForList("SELECT i.*,s.subject_code,s.subject_name FROM cost_target_item i JOIN cost_subject s ON s.id=i.cost_subject_id AND s.tenant_id=i.tenant_id WHERE i.tenant_id=? AND i.target_id=? AND i.deleted_flag=0 ORDER BY i.sort_order,i.id", tenant(), targetId);
    }

    private List<Map<String, Object>> forecastItems(Long forecastId) {
        return jdbc.queryForList("SELECT i.*,s.subject_code,s.subject_name FROM cost_forecast_item i JOIN cost_subject s ON s.id=i.cost_subject_id AND s.tenant_id=i.tenant_id WHERE i.tenant_id=? AND i.forecast_id=? ORDER BY s.subject_code,i.id", tenant(), forecastId);
    }

    private List<Map<String, Object>> forecastInputItems(Long projectId, Long targetId, LocalDate date) {
        return jdbc.queryForList("""
                SELECT s.id cost_subject_id,s.subject_code,s.subject_name,
                COALESCE(t.bid_cost_amount,0) bid_cost_amount,COALESCE(t.target_amount,0) target_amount,
                COALESCE(t.responsibility_amount,0) responsibility_amount,t.responsible_user_id,t.responsibility_unit,
                COALESCE(c.committed_amount,0) committed_amount,COALESCE(c.actual_amount,0) actual_amount,
                CASE WHEN COALESCE(c.committed_amount,0)>COALESCE(c.actual_amount,0) THEN COALESCE(c.committed_amount,0)-COALESCE(c.actual_amount,0) ELSE 0 END recommended_remaining_amount
                FROM cost_subject s
                LEFT JOIN cost_target_item t ON t.cost_subject_id=s.id AND t.tenant_id=s.tenant_id AND t.target_id=? AND t.deleted_flag=0
                LEFT JOIN (
                    SELECT ci.cost_subject_id,
                    SUM(CASE WHEN ci.source_type='CT_CONTRACT' AND ci.cost_status='CONFIRMED'
                        AND NOT EXISTS (SELECT 1 FROM ct_contract mc WHERE mc.id=ci.contract_id AND mc.tenant_id=ci.tenant_id AND mc.contract_type='MAIN' AND mc.deleted_flag=0)
                        THEN ci.amount ELSE 0 END) committed_amount,
                    SUM(CASE WHEN ci.source_type IN ('MAT_RECEIPT','MAT_REQUISITION','SUB_MEASURE','VAR_ORDER','CT_CHANGE','BID_COST','BID_COST_TRANSFERRED','OVERHEAD_ALLOCATION') AND ci.cost_status IN('CONFIRMED','POSTED') THEN ci.amount ELSE 0 END) actual_amount
                    FROM cost_item ci WHERE ci.tenant_id=? AND ci.project_id=? AND ci.deleted_flag=0 AND ci.cost_subject_id IS NOT NULL AND (ci.cost_date IS NULL OR ci.cost_date<=?) GROUP BY ci.cost_subject_id
                ) c ON c.cost_subject_id=s.id
                WHERE s.tenant_id=? AND s.deleted_flag=0 AND (t.id IS NOT NULL OR c.cost_subject_id IS NOT NULL)
                ORDER BY s.subject_code,s.id
                """, targetId, tenant(), projectId, date, tenant());
    }

    private List<Map<String, Object>> corrections(Long forecastId) {
        return jdbc.queryForList("SELECT c.*,u.real_name responsible_user_name FROM cost_corrective_action c LEFT JOIN sys_user u ON u.id=c.responsible_user_id AND u.tenant_id=c.tenant_id WHERE c.tenant_id=? AND c.forecast_id=? AND c.deleted_flag=0 ORDER BY c.created_at,c.id", tenant(), forecastId);
    }

    private List<Map<String, Object>> latestSummary(Long projectId) {
        return jdbc.queryForList("SELECT * FROM cost_summary WHERE tenant_id=? AND project_id=? AND deleted_flag=0 AND summary_date=(SELECT MAX(summary_date) FROM cost_summary WHERE tenant_id=? AND project_id=? AND deleted_flag=0) ORDER BY cost_subject_id", tenant(), projectId, tenant(), projectId);
    }

    private Map<String, Object> requireForecast(Long id, boolean lock) {
        return queryOne("SELECT * FROM cost_forecast WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""), "COST_FORECAST_NOT_FOUND", "完工预测不存在", id, tenant());
    }

    private Map<String, Object> requireCorrection(Long id, boolean lock) {
        return queryOne("SELECT * FROM cost_corrective_action WHERE id=? AND tenant_id=? AND deleted_flag=0" + (lock ? " FOR UPDATE" : ""), "COST_CORRECTIVE_NOT_FOUND", "成本纠偏措施不存在", id, tenant());
    }

    private void requireEnabledUser(Long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND tenant_id=? AND status='ENABLE' AND deleted_flag=0", Integer.class, userId, tenant());
        if (count == null || count != 1) throw error("COST_RESPONSIBLE_USER_INVALID", "责任人不存在、跨租户或已停用");
    }

    private Map<String, Object> queryOne(String sql, String code, String message, Object... args) {
        try { return jdbc.queryForMap(sql, args); }
        catch (EmptyResultDataAccessException e) { throw error(code, message); }
    }

    private Long tenant() { return UserContext.getCurrentTenantId(); }
    private Long user() { return UserContext.getCurrentUserId(); }
    private static BusinessException error(String code, String message) { return new BusinessException(code, message); }
    private static long id(Map<String, Object> row) { return idValue(row.get("id")); }
    private static long idValue(Object value) { if (value instanceof Number n) return n.longValue(); return Long.parseLong(String.valueOf(value)); }
    private static Long idValueNullable(Object value) { return value == null ? null : idValue(value); }
    private static int intValue(Object value) { if (value instanceof Number n) return n.intValue(); return Integer.parseInt(String.valueOf(value)); }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String stringNullable(Object value) { return value == null ? null : String.valueOf(value); }
    private static LocalDate localDate(Object value) { if (value instanceof LocalDate d) return d; if (value instanceof java.sql.Date d) return d.toLocalDate(); return LocalDate.parse(String.valueOf(value)); }
    private static BigDecimal money(Object value) { BigDecimal amount = value == null ? BigDecimal.ZERO : value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value)); return amount.setScale(2, RoundingMode.HALF_UP); }
}
