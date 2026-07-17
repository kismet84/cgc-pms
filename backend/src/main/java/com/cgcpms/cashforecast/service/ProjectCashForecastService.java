package com.cgcpms.cashforecast.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashforecast.dto.CashForecastModels.*;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectCashForecastService {
    private static final Set<String> SCENARIOS = Set.of("BASE", "OPTIMISTIC", "CONSERVATIVE");
    private static final Set<String> ACTION_TYPES = Set.of("ACCELERATE_COLLECTION", "DEFER_PAYMENT", "FUND_TRANSFER", "FINANCING");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ProjectAccessChecker projectAccessChecker;

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createCycle(CycleRequest request) {
        projectAccessChecker.checkAccess(request.projectId(), "维护资金预测");
        requireProjectOperational(request.projectId());
        validateDates(request.asOfDate(), request.horizonStart(), request.horizonEnd());
        String scenario = upper(request.scenario());
        if (!SCENARIOS.contains(scenario)) throw error("CASH_FORECAST_SCENARIO_INVALID", "预测场景不合法");
        if (request.previousCycleId()!=null) {
            Map<String,Object> previous = cycle(request.previousCycleId(), false);
            if (!Objects.equals(longValue(previous.get("project_id")), request.projectId())) throw error("CASH_FORECAST_PREVIOUS_MISMATCH", "滚动来源不属于同一项目");
        }
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM cash_forecast_cycle WHERE tenant_id=? AND project_id=? AND scenario=?", Integer.class, tenant(), request.projectId(), scenario);
        Long id=IdWorker.getId();
        String code="CF-"+request.projectId()+"-"+scenario+"-V"+next;
        jdbc.update("INSERT INTO cash_forecast_cycle(id,tenant_id,project_id,cycle_code,forecast_name,as_of_date,horizon_start,horizon_end,scenario,opening_balance,status,version_no,previous_cycle_id,source_cutoff_at,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,CURRENT_TIMESTAMP,0,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",
                id,tenant(),request.projectId(),code,request.forecastName().trim(),request.asOfDate(),request.horizonStart(),request.horizonEnd(),scenario,money(request.openingBalance()),next,request.previousCycleId(),user(),user());
        generateLines(id);
        audit("CASH_FORECAST_CREATED", id, request.projectId(), Map.of("cycleCode",code,"scenario",scenario,"version",next));
        return trace(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> regenerate(Long cycleId) {
        Map<String,Object> cycle=cycle(cycleId,true); requireStatus(cycle,"DRAFT","CASH_FORECAST_NOT_DRAFT");
        if (count("SELECT COUNT(*) FROM cash_funding_action WHERE tenant_id=? AND cycle_id=?",tenant(),cycleId)>0) throw error("CASH_FORECAST_ACTIONS_EXIST","已有缺口措施时不得重算，请新建滚动版本");
        generateLines(cycleId);
        jdbc.update("UPDATE cash_forecast_cycle SET source_cutoff_at=CURRENT_TIMESTAMP,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",user(),cycleId);
        audit("CASH_FORECAST_REGENERATED",cycleId,longValue(cycle.get("project_id")),Map.of());
        return trace(cycleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> submit(Long cycleId) {
        Map<String,Object> cycle=cycle(cycleId,true); requireStatus(cycle,"DRAFT","CASH_FORECAST_NOT_DRAFT");
        BigDecimal gap=decimal(jdbc.queryForObject("SELECT COALESCE(SUM(gap_amount),0) FROM cash_forecast_line WHERE tenant_id=? AND cycle_id=?",BigDecimal.class,tenant(),cycleId));
        BigDecimal covered=decimal(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM cash_funding_action WHERE tenant_id=? AND cycle_id=? AND status IN ('SUBMITTED','APPROVED','COMPLETED')",BigDecimal.class,tenant(),cycleId));
        if(gap.compareTo(covered)>0) throw error("CASH_FORECAST_GAP_UNCOVERED","资金缺口必须由已提交措施足额覆盖后才能提交预测");
        jdbc.update("UPDATE cash_forecast_cycle SET status='SUBMITTED',submitted_by=?,submitted_at=CURRENT_TIMESTAMP,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",user(),user(),cycleId);
        audit("CASH_FORECAST_SUBMITTED",cycleId,longValue(cycle.get("project_id")),Map.of("gap",gap,"covered",covered));
        return trace(cycleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> approve(Long cycleId, ApprovalRequest request) {
        Map<String,Object> cycle=cycle(cycleId,true); requireStatus(cycle,"SUBMITTED","CASH_FORECAST_NOT_SUBMITTED");
        if(Objects.equals(longValue(cycle.get("created_by")),user())) throw error("CASH_FORECAST_APPROVAL_SEGREGATION_REQUIRED","预测编制人与审批人不能相同");
        if(!request.approved()) {
            jdbc.update("UPDATE cash_forecast_cycle SET status='DRAFT',submitted_by=NULL,submitted_at=NULL,approval_comment=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",request.comment().trim(),user(),cycleId);
            audit("CASH_FORECAST_REJECTED",cycleId,longValue(cycle.get("project_id")),Map.of("comment",request.comment())); return trace(cycleId);
        }
        if(count("SELECT COUNT(*) FROM cash_funding_action WHERE tenant_id=? AND cycle_id=? AND status IN ('PROPOSED','SUBMITTED')",tenant(),cycleId)>0) throw error("CASH_FORECAST_ACTION_APPROVAL_REQUIRED","缺口措施全部审批后才能批准预测");
        jdbc.update("UPDATE cash_forecast_cycle SET status='SUPERSEDED',version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND project_id=? AND scenario=? AND status='APPROVED' AND id<>?",user(),tenant(),cycle.get("project_id"),cycle.get("scenario"),cycleId);
        jdbc.update("UPDATE cash_forecast_cycle SET status='APPROVED',approved_by=?,approved_at=CURRENT_TIMESTAMP,approval_comment=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",user(),request.comment().trim(),user(),cycleId);
        audit("CASH_FORECAST_APPROVED",cycleId,longValue(cycle.get("project_id")),Map.of("comment",request.comment())); return trace(cycleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> refreshActual(Long cycleId) {
        Map<String,Object> cycle=cycle(cycleId,true);
        if(!Set.of("APPROVED","SUPERSEDED").contains(String.valueOf(cycle.get("status")))) throw error("CASH_FORECAST_NOT_APPROVED","仅已批准预测允许刷新实际偏差");
        List<Map<String,Object>> lines=lines(cycleId);
        for(Map<String,Object> line:lines){LocalDate date=localDate(line.get("forecast_date"));
            BigDecimal in=actual(longValue(cycle.get("project_id")),date,"IN"),out=actual(longValue(cycle.get("project_id")),date,"OUT");
            jdbc.update("UPDATE cash_forecast_line SET actual_inflow=?,actual_outflow=?,inflow_variance=?-planned_inflow,outflow_variance=?-planned_outflow,actual_refreshed_at=CURRENT_TIMESTAMP WHERE id=?",in,out,in,out,line.get("id"));}
        audit("CASH_FORECAST_ACTUAL_REFRESHED",cycleId,longValue(cycle.get("project_id")),Map.of("lineCount",lines.size())); return trace(cycleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> roll(Long cycleId, RollRequest request) {
        Map<String,Object> previous=cycle(cycleId,false); requireStatus(previous,"APPROVED","CASH_FORECAST_NOT_APPROVED");
        if(!request.asOfDate().isAfter(localDate(previous.get("as_of_date")))) throw error("CASH_FORECAST_ROLL_DATE_INVALID","滚动基准日必须晚于原版本");
        BigDecimal opening=money(decimal(previous.get("opening_balance")).add(actual(longValue(previous.get("project_id")),localDate(previous.get("horizon_start")),request.asOfDate(),"IN")).subtract(actual(longValue(previous.get("project_id")),localDate(previous.get("horizon_start")),request.asOfDate(),"OUT"))).max(BigDecimal.ZERO);
        return createCycle(new CycleRequest(longValue(previous.get("project_id")),request.forecastName(),request.asOfDate(),request.asOfDate(),request.horizonEnd(),String.valueOf(previous.get("scenario")),opening,cycleId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> createAction(Long cycleId, FundingActionRequest request) {
        Map<String,Object> cycle=cycle(cycleId,true); requireStatus(cycle,"DRAFT","CASH_FORECAST_NOT_DRAFT");
        Map<String,Object> line=one("SELECT * FROM cash_forecast_line WHERE id=? AND tenant_id=? AND cycle_id=?",request.lineId(),tenant(),cycleId);
        if(line==null) throw error("CASH_FORECAST_LINE_NOT_FOUND","预测行不存在");
        if(!request.plannedDate().equals(localDate(line.get("forecast_date")))) throw error("CASH_FUNDING_ACTION_DATE_MISMATCH","资金措施日期必须与缺口预测日一致");
        String type=upper(request.actionType()); if(!ACTION_TYPES.contains(type)) throw error("CASH_FUNDING_ACTION_TYPE_INVALID","资金措施类型不合法");
        BigDecimal gap=decimal(line.get("gap_amount")),existing=decimal(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM cash_funding_action WHERE tenant_id=? AND line_id=? AND status<>'CANCELLED'",BigDecimal.class,tenant(),request.lineId()));
        BigDecimal amount=money(request.amount()); if(existing.add(amount).compareTo(gap)>0) throw error("CASH_FUNDING_ACTION_EXCEEDS_GAP","资金措施金额不得超过该日未覆盖缺口");
        Long id=IdWorker.getId();jdbc.update("INSERT INTO cash_funding_action(id,tenant_id,cycle_id,line_id,project_id,action_type,planned_date,amount,reason,status,source_type,source_id,requested_by,version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'PROPOSED',?,?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,tenant(),cycleId,request.lineId(),cycle.get("project_id"),type,request.plannedDate(),amount,request.reason().trim(),blankUpper(request.sourceType()),request.sourceId(),user());
        audit("CASH_FUNDING_ACTION_CREATED",cycleId,longValue(cycle.get("project_id")),Map.of("actionId",id,"type",type,"amount",amount)); return action(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> submitAction(Long id){Map<String,Object>a=action(id);Map<String,Object>c=cycle(longValue(a.get("cycle_id")),true);a=actionForUpdate(id);requireStatus(c,"DRAFT","CASH_FORECAST_NOT_DRAFT");if(!"PROPOSED".equals(a.get("status")))throw error("CASH_FUNDING_ACTION_NOT_PROPOSED","仅拟定措施允许提交");jdbc.update("UPDATE cash_funding_action SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);return action(id);}

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> approveAction(Long id, FundingActionApprovalRequest request){Map<String,Object>a=action(id);Map<String,Object>c=cycle(longValue(a.get("cycle_id")),true);a=actionForUpdate(id);if(!Set.of("DRAFT","SUBMITTED").contains(String.valueOf(c.get("status"))))throw error("CASH_FORECAST_ACTION_LIFECYCLE_CLOSED","仅草稿或已提交预测允许审批缺口措施");if(!"SUBMITTED".equals(a.get("status")))throw error("CASH_FUNDING_ACTION_NOT_SUBMITTED","仅已提交措施允许审批");if(Objects.equals(longValue(a.get("requested_by")),user()))throw error("CASH_FUNDING_ACTION_SEGREGATION_REQUIRED","措施申请人与审批人不能相同");String status=request.approved()?"APPROVED":"PROPOSED";jdbc.update("UPDATE cash_funding_action SET status=?,approved_by=?,approved_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",status,user(),id);if(request.approved()&&Set.of("FUND_TRANSFER","FINANCING").contains(a.get("action_type"))){jdbc.update("UPDATE cash_forecast_line SET financing_amount=financing_amount+? WHERE id=?",a.get("amount"),a.get("line_id"));recalculate(longValue(a.get("cycle_id")));}audit(request.approved()?"CASH_FUNDING_ACTION_APPROVED":"CASH_FUNDING_ACTION_REJECTED",longValue(a.get("cycle_id")),longValue(a.get("project_id")),Map.of("actionId",id,"comment",request.comment()));return action(id);}

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> completeAction(Long id, FundingActionCompletionRequest request){Map<String,Object>a=action(id);Map<String,Object>c=cycle(longValue(a.get("cycle_id")),true);a=actionForUpdate(id);if(!Set.of("APPROVED","SUPERSEDED").contains(String.valueOf(c.get("status"))))throw error("CASH_FORECAST_NOT_APPROVED","预测批准后才能完成资金措施");if(!"APPROVED".equals(a.get("status")))throw error("CASH_FUNDING_ACTION_NOT_APPROVED","仅已批准措施允许完成");jdbc.update("UPDATE cash_funding_action SET status='COMPLETED',actual_amount=?,completion_reference=?,completed_by=?,completed_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",money(request.actualAmount()),request.completionReference().trim(),user(),id);audit("CASH_FUNDING_ACTION_COMPLETED",longValue(a.get("cycle_id")),longValue(a.get("project_id")),Map.of("actionId",id,"reference",request.completionReference()));return action(id);}

    public List<Map<String,Object>> cycles(Long projectId){projectAccessChecker.checkAccess(projectId,"查看资金预测");return jdbc.queryForList("SELECT * FROM cash_forecast_cycle WHERE tenant_id=? AND project_id=? ORDER BY scenario,version_no DESC",tenant(),projectId);}
    public Map<String,Object> trace(Long cycleId){Map<String,Object> c=cycle(cycleId,false);Long projectId=longValue(c.get("project_id"));return Map.of("cycle",c,"lines",lines(cycleId),"actions",jdbc.queryForList("SELECT * FROM cash_funding_action WHERE tenant_id=? AND cycle_id=? ORDER BY planned_date,id",tenant(),cycleId),"collectionSchedules",jdbc.queryForList("SELECT * FROM collection_schedule WHERE tenant_id=? AND project_id=? AND planned_date BETWEEN ? AND ? ORDER BY planned_date,id",tenant(),projectId,c.get("horizon_start"),c.get("horizon_end")),"paymentSchedules",jdbc.queryForList("SELECT * FROM payment_schedule WHERE tenant_id=? AND project_id=? AND planned_date BETWEEN ? AND ? ORDER BY planned_date,id",tenant(),projectId,c.get("horizon_start"),c.get("horizon_end")),"actualJournals",jdbc.queryForList("SELECT id,entry_no,direction,amount,business_date,source_type,source_id,status FROM cash_journal_entry WHERE tenant_id=? AND project_id=? AND business_date BETWEEN ? AND ? AND deleted_flag=0 ORDER BY business_date,id",tenant(),projectId,c.get("horizon_start"),c.get("horizon_end")),"auditTrail",jdbc.queryForList("SELECT event_type,operator_id,event_at,payload_json,payload_hash FROM finance_audit_event WHERE tenant_id=? AND business_type='CASH_FORECAST_CYCLE' AND business_id=? ORDER BY event_at,id",tenant(),cycleId));}

    private void generateLines(Long cycleId){Map<String,Object> c=cycle(cycleId,true);jdbc.update("DELETE FROM cash_forecast_line WHERE tenant_id=? AND cycle_id=?",tenant(),cycleId);LocalDate from=localDate(c.get("horizon_start")),to=localDate(c.get("horizon_end"));BigDecimal balance=decimal(c.get("opening_balance"));for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1)){BigDecimal inflow=plannedCollection(longValue(c.get("project_id")),d),outflow=plannedPayment(longValue(c.get("project_id")),d);balance=money(balance.add(inflow).subtract(outflow));BigDecimal gap=balance.signum()<0?balance.abs():BigDecimal.ZERO;Map<String,Object> source=Map.of("collectionScheduleCount",sourceCount("collection_schedule",longValue(c.get("project_id")),d),"paymentScheduleCount",sourceCount("payment_schedule",longValue(c.get("project_id")),d));jdbc.update("INSERT INTO cash_forecast_line(id,tenant_id,cycle_id,forecast_date,planned_inflow,planned_outflow,financing_amount,projected_balance,gap_amount,actual_inflow,actual_outflow,inflow_variance,outflow_variance,source_summary_json) VALUES(?,?,?,?,?,?,0,?,?,0,0,0,0,?)",IdWorker.getId(),tenant(),cycleId,d,inflow,outflow,balance,gap,json(source));}}
    private void recalculate(Long cycleId){Map<String,Object> c=cycle(cycleId,true);BigDecimal balance=decimal(c.get("opening_balance"));for(Map<String,Object> l:lines(cycleId)){balance=money(balance.add(decimal(l.get("planned_inflow"))).add(decimal(l.get("financing_amount"))).subtract(decimal(l.get("planned_outflow"))));jdbc.update("UPDATE cash_forecast_line SET projected_balance=?,gap_amount=? WHERE id=?",balance,balance.signum()<0?balance.abs():BigDecimal.ZERO,l.get("id"));}}
    private BigDecimal plannedCollection(Long p,LocalDate d){return decimal(jdbc.queryForObject("SELECT COALESCE(SUM(planned_amount-collected_amount),0) FROM collection_schedule WHERE tenant_id=? AND project_id=? AND planned_date=? AND status IN ('PLANNED','PARTIALLY_COLLECTED')",BigDecimal.class,tenant(),p,d));}
    private BigDecimal plannedPayment(Long p,LocalDate d){return decimal(jdbc.queryForObject("SELECT COALESCE(SUM(planned_amount-paid_amount),0) FROM payment_schedule WHERE tenant_id=? AND project_id=? AND planned_date=? AND status IN ('PLANNED','PARTIALLY_PAID')",BigDecimal.class,tenant(),p,d));}
    private long sourceCount(String table,Long p,LocalDate d){return count("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=? AND project_id=? AND planned_date=?",tenant(),p,d);}
    private BigDecimal actual(Long p,LocalDate d,String direction){return decimal(jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN status='REVERSED' AND reversal_entry_id IS NULL THEN 0 ELSE amount END),0) FROM cash_journal_entry WHERE tenant_id=? AND project_id=? AND business_date=? AND direction=? AND status IN ('ARCHIVED','REVERSED') AND deleted_flag=0",BigDecimal.class,tenant(),p,d,direction));}
    private BigDecimal actual(Long p,LocalDate from,LocalDate to,String direction){return decimal(jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN status='REVERSED' AND reversal_entry_id IS NULL THEN 0 ELSE amount END),0) FROM cash_journal_entry WHERE tenant_id=? AND project_id=? AND business_date BETWEEN ? AND ? AND direction=? AND status IN ('ARCHIVED','REVERSED') AND deleted_flag=0",BigDecimal.class,tenant(),p,from,to,direction));}
    private void requireProjectOperational(Long projectId){Map<String,Object> p=one("SELECT status FROM pm_project WHERE id=? AND tenant_id=? AND deleted_flag=0",projectId,tenant());if(p==null)throw error("PROJECT_NOT_FOUND","项目不存在");if(Set.of("SUSPENDED","CLOSED").contains(String.valueOf(p.get("status"))))throw error("CASH_FORECAST_PROJECT_NOT_OPERATIONAL","暂停或关闭项目不得新建资金预测");}
    private void validateDates(LocalDate asOf,LocalDate from,LocalDate to){if(asOf.isAfter(from)||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>365)throw error("CASH_FORECAST_HORIZON_INVALID","预测区间必须从基准日或之后开始且不超过366天");}
    private Map<String,Object> cycle(Long id,boolean lock){Map<String,Object> c=one("SELECT * FROM cash_forecast_cycle WHERE id=? AND tenant_id=?"+(lock?" FOR UPDATE":""),id,tenant());if(c==null)throw error("CASH_FORECAST_NOT_FOUND","资金预测不存在");projectAccessChecker.checkAccess(longValue(c.get("project_id")),"访问资金预测");return c;}
    private List<Map<String,Object>> lines(Long id){return jdbc.queryForList("SELECT * FROM cash_forecast_line WHERE tenant_id=? AND cycle_id=? ORDER BY forecast_date",tenant(),id);}
    private Map<String,Object> action(Long id){Map<String,Object>a=one("SELECT * FROM cash_funding_action WHERE id=? AND tenant_id=?",id,tenant());if(a==null)throw error("CASH_FUNDING_ACTION_NOT_FOUND","资金措施不存在");projectAccessChecker.checkAccess(longValue(a.get("project_id")),"访问资金措施");return a;}
    private Map<String,Object> actionForUpdate(Long id){Map<String,Object>a=one("SELECT * FROM cash_funding_action WHERE id=? AND tenant_id=? FOR UPDATE",id,tenant());if(a==null)throw error("CASH_FUNDING_ACTION_NOT_FOUND","资金措施不存在");projectAccessChecker.checkAccess(longValue(a.get("project_id")),"维护资金措施");return a;}
    private void requireStatus(Map<String,Object> row,String expected,String code){if(!expected.equals(row.get("status")))throw error(code,"当前状态不允许执行该操作");}
    private void audit(String event,Long cycleId,Long projectId,Object payload){String body=json(payload);jdbc.update("INSERT INTO finance_audit_event(id,tenant_id,event_type,business_type,business_id,project_id,operator_id,event_at,archive_bucket,payload_json,payload_hash) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,'HOT',?,?)",IdWorker.getId(),tenant(),event,"CASH_FORECAST_CYCLE",cycleId,projectId,user(),body,sha256(body));}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.getFirst();}
    private long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    private Long tenant(){Long v=UserContext.getCurrentTenantId();if(v==null)throw error("TENANT_CONTEXT_REQUIRED","缺少租户上下文");return v;}
    private Long user(){Long v=UserContext.getCurrentUserId();if(v==null)throw error("USER_CONTEXT_REQUIRED","缺少用户上下文");return v;}
    private String json(Object v){try{return objectMapper.writeValueAsString(v);}catch(JsonProcessingException e){throw error("CASH_FORECAST_JSON_ERROR","资金预测数据序列化失败");}}
    private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String upper(String v){return v==null?"":v.trim().toUpperCase();} private static String blankUpper(String v){String x=upper(v);return x.isEmpty()?null:x;}
    private static BigDecimal money(BigDecimal v){return(v==null?BigDecimal.ZERO:v).setScale(2,RoundingMode.HALF_UP);} private static BigDecimal decimal(Object v){return money(v==null?BigDecimal.ZERO:new BigDecimal(v.toString()));}
    private static Long longValue(Object v){return v==null?null:Long.valueOf(v.toString());} private static LocalDate localDate(Object v){if(v instanceof LocalDate d)return d;if(v instanceof Timestamp t)return t.toLocalDateTime().toLocalDate();return LocalDate.parse(v.toString());}
    private static BusinessException error(String c,String m){return new BusinessException(c,m);}
}
