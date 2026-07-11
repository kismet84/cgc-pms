package com.cgcpms.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.file.entity.SysFile;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(*) FROM sys_file
            WHERE tenant_id = #{tenantId}
              AND business_type = #{businessType}
              AND business_id = #{businessId}
              AND deleted_flag = 0
            """)
    long countActiveByBusiness(@Param("tenantId") Long tenantId,
                               @Param("businessType") String businessType,
                               @Param("businessId") Long businessId);
}
