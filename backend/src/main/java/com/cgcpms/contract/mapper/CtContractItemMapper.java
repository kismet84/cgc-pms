package com.cgcpms.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.contract.entity.CtContractItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CtContractItemMapper extends BaseMapper<CtContractItem> {
    @Select("""
            SELECT id, tenant_id, contract_id, material_id, item_code, item_name, item_spec, unit,
                   quantity, unit_price, amount, tax_rate, tax_amount, amount_without_tax,
                   sort_order, created_by, created_at, updated_by, updated_at, deleted_flag, remark
            FROM ct_contract_item
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
            FOR UPDATE
            """)
    CtContractItem selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
