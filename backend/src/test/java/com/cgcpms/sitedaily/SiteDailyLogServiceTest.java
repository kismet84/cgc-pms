package com.cgcpms.sitedaily;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.site.service.SiteDailyLogService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.cgcpms.common.exception.BusinessException;

class SiteDailyLogServiceTest {
    @AfterEach void clear() { UserContext.clear(); }

    @Test
    void detailChecksProjectAccess() {
        SiteDailyLogMapper mapper = mock(SiteDailyLogMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker checker = mock(ProjectAccessChecker.class);
        SiteDailyLogService service = new SiteDailyLogService(mapper, projectMapper, checker);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        SiteDailyLog log = new SiteDailyLog();
        log.setId(31L); log.setTenantId(11L); log.setProjectId(21L);
        log.setReportDate(LocalDate.of(2099, 1, 1));
        log.setConstructionContent("施工内容");
        log.setWeatherSummary("晴，午后有风");
        log.setOnSiteHeadcount(0);
        log.setStatus("DRAFT");
        when(mapper.selectById(31L)).thenReturn(log);

        var detail = service.getById(31L);

        verify(checker).checkAccess(21L, "访问现场日报");
        assertEquals("晴，午后有风", detail.getWeatherSummary());
        assertEquals(0, detail.getOnSiteHeadcount());
    }

    @Test
    void crossTenantDetailIsHiddenBeforeProjectCheck() {
        SiteDailyLogMapper mapper = mock(SiteDailyLogMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker checker = mock(ProjectAccessChecker.class);
        SiteDailyLogService service = new SiteDailyLogService(mapper, projectMapper, checker);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        SiteDailyLog log = new SiteDailyLog();
        log.setId(32L); log.setTenantId(12L); log.setProjectId(22L);
        when(mapper.selectById(32L)).thenReturn(log);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getById(32L));

        assertEquals("SITE_DAILY_LOG_NOT_FOUND", error.getCode());
        verifyNoInteractions(checker);
    }

    @Test
    void createRequiresProjectAccess() {
        SiteDailyLogMapper mapper = mock(SiteDailyLogMapper.class);
        PmProjectMapper projectMapper = mock(PmProjectMapper.class);
        ProjectAccessChecker checker = mock(ProjectAccessChecker.class);
        SiteDailyLogService service = new SiteDailyLogService(mapper, projectMapper, checker);
        UserContext.set(Jwts.claims().add("userId", 7L).add("tenantId", 11L).build());
        SiteDailyLog log = new SiteDailyLog();
        log.setProjectId(21L); log.setReportDate(LocalDate.of(2099, 1, 2));
        log.setConstructionContent("施工内容");
        doAnswer(invocation -> { log.setId(33L); return 1; }).when(mapper).insert(log);

        service.create(log);

        verify(checker).checkAccess(21L, "创建现场日报");
        assertEquals(11L, log.getTenantId());
        assertEquals("DRAFT", log.getStatus());
    }
}
