package com.cgcpms.site.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.quality.entity.QualityInspectionRecord;
import com.cgcpms.quality.entity.QualitySafetyIssue;
import com.cgcpms.quality.mapper.QualityInspectionRecordMapper;
import com.cgcpms.quality.mapper.QualitySafetyIssueMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.site.vo.SiteDailyQualitySafetyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SiteDailyQualitySafetyService {
    private final SiteDailyLogMapper dailyLogMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final QualityInspectionRecordMapper inspectionMapper;
    private final QualitySafetyIssueMapper issueMapper;

    public List<SiteDailyQualitySafetyVO> listForDailyLog(Long dailyLogId) {
        Long tenantId = UserContext.getCurrentTenantId();
        SiteDailyLog dailyLog = dailyLogMapper.selectById(dailyLogId);
        if (dailyLog == null || !tenantId.equals(dailyLog.getTenantId())) {
            throw new BusinessException("SITE_DAILY_LOG_NOT_FOUND", "现场日报不存在");
        }
        projectAccessChecker.checkAccess(dailyLog.getProjectId(), "查询现场日报质量安全事实");

        List<QualityInspectionRecord> inspections = inspectionMapper.selectList(
                new LambdaQueryWrapper<QualityInspectionRecord>()
                        .eq(QualityInspectionRecord::getTenantId, tenantId)
                        .eq(QualityInspectionRecord::getProjectId, dailyLog.getProjectId())
                        .eq(QualityInspectionRecord::getInspectionDate, dailyLog.getReportDate())
                        .eq(QualityInspectionRecord::getStatus, "SUBMITTED")
                        .orderByAsc(QualityInspectionRecord::getInspectionCode)
                        .orderByAsc(QualityInspectionRecord::getId));
        if (inspections.isEmpty()) return List.of();

        List<Long> inspectionIds = inspections.stream().map(QualityInspectionRecord::getId).toList();
        List<QualitySafetyIssue> issues = issueMapper.selectList(new LambdaQueryWrapper<QualitySafetyIssue>()
                .eq(QualitySafetyIssue::getTenantId, tenantId)
                .eq(QualitySafetyIssue::getProjectId, dailyLog.getProjectId())
                .in(QualitySafetyIssue::getInspectionId, inspectionIds));
        Map<Long, IssueCounts> countsByInspection = new HashMap<>();
        for (QualitySafetyIssue issue : issues) {
            IssueCounts counts = countsByInspection.computeIfAbsent(issue.getInspectionId(), ignored -> new IssueCounts());
            counts.total++;
            if ("HIGH".equals(issue.getSeverity())) counts.high++;
            if (!"CLOSED".equals(issue.getStatus())) counts.open++;
        }

        return inspections.stream().map(inspection -> {
            IssueCounts counts = countsByInspection.getOrDefault(inspection.getId(), new IssueCounts());
            SiteDailyQualitySafetyVO result = new SiteDailyQualitySafetyVO();
            result.setInspectionId(inspection.getId().toString());
            result.setInspectionCode(inspection.getInspectionCode());
            result.setLocation(inspection.getLocation());
            result.setConclusion(inspection.getConclusion());
            result.setIssueCount(counts.total);
            result.setHighSeverityIssueCount(counts.high);
            result.setOpenIssueCount(counts.open);
            return result;
        }).toList();
    }

    private static final class IssueCounts {
        private int total;
        private int high;
        private int open;
    }
}
