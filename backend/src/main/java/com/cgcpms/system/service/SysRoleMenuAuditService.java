package com.cgcpms.system.service;

import com.cgcpms.system.entity.SysRoleMenuAuditSnapshot;
import com.cgcpms.system.mapper.SysRoleMenuAuditSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleMenuAuditService {

    private final SysRoleMenuAuditSnapshotMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(Long tenantId, Long operatorId, Long roleId,
                       List<Long> beforeMenuIds, List<Long> afterMenuIds,
                       boolean success, String errorSummary) {
        SysRoleMenuAuditSnapshot snapshot = new SysRoleMenuAuditSnapshot();
        snapshot.setTenantId(tenantId);
        snapshot.setOperatorId(operatorId);
        snapshot.setRoleId(roleId);
        snapshot.setBeforeMenuIds(formatMenuIds(beforeMenuIds));
        snapshot.setAfterMenuIds(formatMenuIds(afterMenuIds));
        snapshot.setSuccessFlag(success ? 1 : 0);
        snapshot.setErrorSummary(errorSummary);
        snapshot.setCreatedAt(LocalDateTime.now());
        mapper.insert(snapshot);
    }

    private String formatMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return "[]";
        }
        return "[" + String.join(",", menuIds.stream().map(String::valueOf).toList()) + "]";
    }
}
