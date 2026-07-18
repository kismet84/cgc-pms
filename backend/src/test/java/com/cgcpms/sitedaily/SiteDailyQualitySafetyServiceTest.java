package com.cgcpms.sitedaily;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.quality.entity.QualityInspectionRecord;
import com.cgcpms.quality.entity.QualitySafetyIssue;
import com.cgcpms.quality.mapper.QualityInspectionRecordMapper;
import com.cgcpms.quality.mapper.QualitySafetyIssueMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.site.service.SiteDailyQualitySafetyService;
import com.cgcpms.site.vo.SiteDailyQualitySafetyVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SiteDailyQualitySafetyServiceTest {
    private SiteDailyLogMapper dailyLogMapper;
    private ProjectAccessChecker projectAccessChecker;
    private QualityInspectionRecordMapper inspectionMapper;
    private QualitySafetyIssueMapper issueMapper;
    private SiteDailyQualitySafetyService service;

    @BeforeEach
    void setUp() {
        dailyLogMapper = mock(SiteDailyLogMapper.class);
        projectAccessChecker = mock(ProjectAccessChecker.class);
        inspectionMapper = mock(QualityInspectionRecordMapper.class);
        issueMapper = mock(QualitySafetyIssueMapper.class);
        service = new SiteDailyQualitySafetyService(
                dailyLogMapper, projectAccessChecker, inspectionMapper, issueMapper);
        TestUserContext.setAdmin(11L, 1L);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void hidesCrossTenantDailyLogBeforeAnyProjectOrQualityQuery() {
        SiteDailyLog foreignLog = dailyLog(12L, 21L, LocalDate.of(2099, 6, 1));
        when(dailyLogMapper.selectById(99L)).thenReturn(foreignLog);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.listForDailyLog(99L));

        assertEquals("SITE_DAILY_LOG_NOT_FOUND", error.getCode());
        verifyNoInteractions(projectAccessChecker, inspectionMapper, issueMapper);
    }

    @Test
    void aggregatesIssueCountsForSubmittedInspectionsInDailyScope() {
        LocalDate reportDate = LocalDate.of(2099, 6, 1);
        when(dailyLogMapper.selectById(99L)).thenReturn(dailyLog(11L, 21L, reportDate));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(
                inspection(101L, "QS-001", "A区", "合格"),
                inspection(102L, "QS-002", "B区", "限期整改")));
        when(issueMapper.selectList(any())).thenReturn(List.of(
                issue(101L, "HIGH", "RECTIFYING"),
                issue(101L, "LOW", "CLOSED"),
                issue(102L, "MEDIUM", "PENDING_REINSPECTION")));

        List<SiteDailyQualitySafetyVO> result = service.listForDailyLog(99L);

        verify(projectAccessChecker).checkAccess(21L, "查询现场日报质量安全事实");
        assertEquals(2, result.size());
        assertEquals("101", result.get(0).getInspectionId());
        assertEquals(2, result.get(0).getIssueCount());
        assertEquals(1, result.get(0).getHighSeverityIssueCount());
        assertEquals(1, result.get(0).getOpenIssueCount());
        assertEquals(1, result.get(1).getIssueCount());
        assertEquals(0, result.get(1).getHighSeverityIssueCount());
        assertEquals(1, result.get(1).getOpenIssueCount());

        ArgumentCaptor<Wrapper<QualityInspectionRecord>> inspectionQuery = wrapperCaptor();
        verify(inspectionMapper).selectList(inspectionQuery.capture());
        assertQueryContains(inspectionQuery.getValue(), "tenant_id", "project_id", "inspection_date", "status");
        assertTrue(paramValues(inspectionQuery.getValue()).containsValue(11L));
        assertTrue(paramValues(inspectionQuery.getValue()).containsValue(21L));
        assertTrue(paramValues(inspectionQuery.getValue()).containsValue(reportDate));
        assertTrue(paramValues(inspectionQuery.getValue()).containsValue("SUBMITTED"));

        ArgumentCaptor<Wrapper<QualitySafetyIssue>> issueQuery = wrapperCaptor();
        verify(issueMapper).selectList(issueQuery.capture());
        assertQueryContains(issueQuery.getValue(), "tenant_id", "project_id", "inspection_id");
        assertTrue(paramValues(issueQuery.getValue()).containsValue(101L));
        assertTrue(paramValues(issueQuery.getValue()).containsValue(102L));
    }

    @Test
    void doesNotQueryIssuesWhenNoSubmittedInspectionExists() {
        when(dailyLogMapper.selectById(99L)).thenReturn(
                dailyLog(11L, 21L, LocalDate.of(2099, 6, 1)));
        when(inspectionMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.listForDailyLog(99L).isEmpty());

        verify(projectAccessChecker).checkAccess(21L, "查询现场日报质量安全事实");
        verifyNoInteractions(issueMapper);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private static void assertQueryContains(Wrapper<?> wrapper, String... columns) {
        String sql = wrapper.getSqlSegment();
        for (String column : columns) assertTrue(sql.contains(column), sql);
    }

    private static java.util.Map<String, Object> paramValues(Wrapper<?> wrapper) {
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    }

    private static SiteDailyLog dailyLog(Long tenantId, Long projectId, LocalDate reportDate) {
        SiteDailyLog log = new SiteDailyLog();
        log.setId(99L);
        log.setTenantId(tenantId);
        log.setProjectId(projectId);
        log.setReportDate(reportDate);
        return log;
    }

    private static QualityInspectionRecord inspection(Long id, String code, String location, String conclusion) {
        QualityInspectionRecord record = new QualityInspectionRecord();
        record.setId(id);
        record.setInspectionCode(code);
        record.setLocation(location);
        record.setConclusion(conclusion);
        return record;
    }

    private static QualitySafetyIssue issue(Long inspectionId, String severity, String status) {
        QualitySafetyIssue issue = new QualitySafetyIssue();
        issue.setInspectionId(inspectionId);
        issue.setSeverity(severity);
        issue.setStatus(status);
        return issue;
    }
}
