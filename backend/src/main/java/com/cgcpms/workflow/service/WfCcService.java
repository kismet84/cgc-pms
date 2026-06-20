package com.cgcpms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.notification.service.NotificationService;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.workflow.entity.WfCc;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfCcMapper;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 审批抄送服务 — 所有方法接收显式参数，不读取 UserContext。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfCcService {

    private final WfCcMapper wfCcMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationService notificationService;

    /**
     * 创建抄送记录并为每个抄送人发送通知。
     *
     * @param instanceId 审批实例ID
     * @param ccUserIds  抄送人用户ID列表（可为空）
     * @param tenantId   租户ID（来自 wfInstance，不来自 UserContext）
     */
    @Transactional
    public void createCc(Long instanceId, List<Long> ccUserIds, Long tenantId) {
        if (ccUserIds == null || ccUserIds.isEmpty()) {
            return;
        }

        WfInstance instance = wfInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("createCc: instance not found, instanceId={}", instanceId);
            return;
        }

        // tenantId from instance, NOT from UserContext
        Long effectiveTenantId = instance.getTenantId();

        // Batch-fetch user names for all cc recipients and validate tenant membership
        Map<Long, SysUser> userMap = Collections.emptyMap();
        if (!ccUserIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(new HashSet<>(ccUserIds));
            userMap = users.stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
            // Validate every cc user belongs to the same tenant as the instance
            for (Long ccUserId : ccUserIds) {
                SysUser ccUser = userMap.get(ccUserId);
                if (ccUser == null || !Objects.equals(ccUser.getTenantId(), effectiveTenantId)) {
                    throw new BusinessException("WORKFLOW_CC_USER_INVALID", "抄送用户不属于当前租户");
                }
            }
        }

        for (Long ccUserId : ccUserIds) {
            WfCc cc = new WfCc();
            cc.setTenantId(effectiveTenantId);
            cc.setInstanceId(instanceId);
            cc.setCcUserId(ccUserId);
            SysUser ccUser = userMap.get(ccUserId);
            cc.setCcUserName(ccUser != null
                    ? (ccUser.getRealName() != null ? ccUser.getRealName() : ccUser.getUsername())
                    : "");
            cc.setBusinessType(instance.getBusinessType());
            cc.setBusinessId(instance.getBusinessId());
            cc.setTitle(instance.getTitle());
            cc.setIsRead(0);
            cc.setCreatedTime(LocalDateTime.now());
            wfCcMapper.insert(cc);

            // Trigger notification for each cc user
            try {
                notificationService.create(
                        effectiveTenantId,
                        ccUserId,
                        "审批抄送: " + instance.getTitle(),
                        "您有一份审批抄送，业务类型: " + instance.getBusinessType()
                                + "，审批标题: " + instance.getTitle(),
                        instance.getBusinessType(),
                        instance.getBusinessId());
            } catch (Exception e) {
                log.error("Failed to send cc notification to userId={}, instanceId={}", ccUserId, instanceId, e);
                // Do not fail the transaction; cc record is already persisted
            }
        }

        log.debug("Created {} cc records for instanceId={}", ccUserIds.size(), instanceId);
    }

    /**
     * 查询用户的抄送列表（分页）。
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param pageNo   页码
     * @param pageSize 每页大小
     */
    public IPage<WfCc> getMyCc(Long userId, Long tenantId, long pageNo, long pageSize) {
        LambdaQueryWrapper<WfCc> wrapper = new LambdaQueryWrapper<WfCc>()
                .eq(WfCc::getTenantId, tenantId)
                .eq(WfCc::getCcUserId, userId)
                .orderByDesc(WfCc::getCreatedTime);

        return wfCcMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }
}
