package com.cgcpms.subcontract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface SubMeasureMapper extends BaseMapper<SubMeasure> {
    @Select("SELECT * FROM sub_measure WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    SubMeasure selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT COALESCE(SUM(i.current_quantity), 0)
              FROM sub_measure_item i
              JOIN sub_measure m ON m.id = i.measure_id
             WHERE i.tenant_id = #{tenantId}
               AND i.contract_item_id = #{contractItemId}
               AND i.deleted_flag = 0 AND m.deleted_flag = 0
               AND m.approval_status = 'APPROVED'
               AND m.id <> #{excludeMeasureId}
            """)
    BigDecimal sumApprovedQuantity(@Param("tenantId") Long tenantId,
                                   @Param("contractItemId") Long contractItemId,
                                   @Param("excludeMeasureId") Long excludeMeasureId);
}
