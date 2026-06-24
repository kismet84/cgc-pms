package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.notification.service.NotificationService;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 项目归档条件异步检测器。
 * <p>
 * 在结算审批通过后触发，异步检查项目是否满足归档条件：
 * <ol>
 *   <li>合同已结算或已终止</li>
 *   <li>付款已全部付清</li>
 *   <li>结算已全部定案</li>
 *   <li>无运行中的审批流程</li>
 * </ol>
 * 若全部满足，向项目经理发送站内通知："项目可归档"。
 * </p>
 * <p>
 * <b>设计边界（第一期）</b>：仅做"可归档通知"，不在后台自动执行 archive。
 * 自动归档属于高风险状态切换，本阶段目标是减少人工判断成本，而不是替代人工最终确认。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectArchiveNotifier {

    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final PayApplicationMapper payApplicationMapper;
    private final StlSettlementMapper stlSettlementMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final NotificationService notificationService;

    /**
     * 异步检查项目归档条件。
     * 结算审批通过后调用此方法（不影响主链路）。
     *
     * @param projectId 项目ID
     * @param tenantId  租户ID
     * @param triggerDescription 触发描述（如 "结算 STL-20240624-001 审批通过"），用于通知上下文
     */
    @Async
    public void checkAndNotify(Long projectId, Long tenantId, String triggerDescription) {
        if (projectId == null || tenantId == null) {
            log.warn("ProjectArchiveNotifier 参数缺失 projectId={} tenantId={}", projectId, tenantId);
            return;
        }

        try {
            log.info("开始异步检查项目归档条件 projectId={} tenantId={} trigger={}",
                    projectId, tenantId, triggerDescription);

            PmProject project = pmProjectMapper.selectById(projectId);
            if (project == null) {
                log.warn("项目不存在 projectId={}", projectId);
                return;
            }
            if (!tenantId.equals(project.getTenantId())) {
                log.warn("租户不匹配 projectId={} tenantId={} projectTenantId={}",
                        projectId, tenantId, project.getTenantId());
                return;
            }

            // 已归档/草稿项目跳过
            if ("ARCHIVED".equals(project.getStatus()) || "DRAFT".equals(project.getStatus())) {
                log.debug("项目状态不适合检查归档 projectId={} status={}", projectId, project.getStatus());
                return;
            }

            // 逐项检查，收集未满足的条件
            StringBuilder blockers = new StringBuilder();

            long activeContracts = ctContractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                    .eq(CtContract::getProjectId, projectId)
                    .eq(CtContract::getTenantId, tenantId)
                    .notIn(CtContract::getContractStatus, "SETTLED", "TERMINATED"));
            if (activeContracts > 0) {
                blockers.append("• ").append(activeContracts).append(" 个合同未完成结算\n");
            }

            long activePayments = payApplicationMapper.selectCount(new LambdaQueryWrapper<PayApplication>()
                    .eq(PayApplication::getProjectId, projectId)
                    .eq(PayApplication::getTenantId, tenantId)
                    .notIn(PayApplication::getPayStatus, "PAID"));
            if (activePayments > 0) {
                blockers.append("• ").append(activePayments).append(" 笔付款未付清\n");
            }

            long activeSettlements = stlSettlementMapper.selectCount(new LambdaQueryWrapper<StlSettlement>()
                    .eq(StlSettlement::getProjectId, projectId)
                    .eq(StlSettlement::getTenantId, tenantId)
                    .isNull(StlSettlement::getFinalizedAt));
            if (activeSettlements > 0) {
                blockers.append("• ").append(activeSettlements).append(" 个结算未定案\n");
            }

            long runningWorkflows = wfInstanceMapper.selectCount(new LambdaQueryWrapper<WfInstance>()
                    .eq(WfInstance::getProjectId, projectId)
                    .eq(WfInstance::getTenantId, tenantId)
                    .eq(WfInstance::getInstanceStatus, "RUNNING"));
            if (runningWorkflows > 0) {
                blockers.append("• ").append(runningWorkflows).append(" 个审批流程进行中\n");
            }

            if (blockers.length() == 0) {
                // 所有条件满足 → 发送通知
                Long pmUserId = project.getProjectManagerId();
                if (pmUserId == null) {
                    log.info("项目 {} 满足归档条件但无项目经理，跳过通知", projectId);
                    return;
                }

                String title = "项目可归档：" + project.getProjectName();
                String content = String.format(
                        "项目「%s」(%s) 已满足归档条件。\n\n"
                                + "触发事件：%s\n"
                                + "• 合同：全部已结算/终止\n"
                                + "• 付款：全部已付清\n"
                                + "• 结算：全部已定案\n"
                                + "• 审批流程：无运行中\n\n"
                                + "请在项目详情页执行归档操作。",
                        project.getProjectName(), project.getProjectCode(),
                        triggerDescription);
                notificationService.create(tenantId, pmUserId, title, content,
                        "PROJECT_ARCHIVE", projectId);
                log.info("项目归档通知已发送 projectId={} pmUserId={}", projectId, pmUserId);
            } else {
                log.debug("项目 {} 尚不满足归档条件:\n{}", projectId, blockers.toString().trim());
            }
        } catch (Exception e) {
            // 异步检查失败不影响主链路
            log.error("项目归档条件检查异常 projectId={} tenantId={}: {}",
                    projectId, tenantId, e.getMessage(), e);
        }
    }
}
