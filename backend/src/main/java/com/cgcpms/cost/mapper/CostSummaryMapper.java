package com.cgcpms.cost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.cost.entity.CostSummary;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface CostSummaryMapper extends BaseMapper<CostSummary> {

    @Delete("""
            DELETE FROM cost_summary
            WHERE tenant_id = #{tenantId}
              AND project_id = #{projectId}
            """)
    int physicalDeleteByTenantAndProject(@Param("tenantId") Long tenantId,
                                         @Param("projectId") Long projectId);

    @Delete("""
            DELETE FROM cost_summary
            WHERE tenant_id = #{tenantId}
              AND project_id = #{projectId}
              AND summary_date = #{summaryDate}
            """)
    int physicalDeleteByTenantProjectAndDate(@Param("tenantId") Long tenantId,
                                              @Param("projectId") Long projectId,
                                              @Param("summaryDate") LocalDate summaryDate);
}
