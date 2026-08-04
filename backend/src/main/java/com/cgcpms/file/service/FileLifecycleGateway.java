package com.cgcpms.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileLifecycleGateway {

    private final ObjectProvider<FileService> fileServiceProvider;
    private final SysFileMapper sysFileMapper;

    public void deleteAllForBusinessCascade(String businessType, Long businessId) {
        FileService fileService = fileServiceProvider.getIfAvailable();
        if (fileService == null) {
            Long count = sysFileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                    .apply("UPPER(TRIM(business_type)) = {0}", businessType.trim().toUpperCase()) // SQL-SAFETY: fixed-sql-fragment — value uses MyBatis parameter binding
                    .eq(SysFile::getBusinessId, businessId)
                    .eq(SysFile::getTenantId, UserContext.getCurrentTenantId()));
            if (count == 0) return;
            throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件生命周期服务不可用，父对象未删除");
        }
        fileService.deleteAllForBusinessCascade(businessType, businessId);
    }
}
