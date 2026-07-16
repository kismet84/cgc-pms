package com.cgcpms.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.contract.entity.BusinessMatterRegistry;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BusinessMatterRegistryMapper extends BaseMapper<BusinessMatterRegistry> {
    @Select("""
            SELECT * FROM business_matter_registry
             WHERE tenant_id = #{tenantId}
               AND project_id = #{projectId}
               AND matter_key = #{matterKey}
               AND active_token = 1
             FOR UPDATE
            """)
    BusinessMatterRegistry selectActiveForUpdate(@Param("tenantId") Long tenantId,
                                                   @Param("projectId") Long projectId,
                                                   @Param("matterKey") String matterKey);
}
