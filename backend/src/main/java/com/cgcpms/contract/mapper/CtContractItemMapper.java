package com.cgcpms.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.contract.entity.CtContractItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CtContractItemMapper extends BaseMapper<CtContractItem> {
    @Select("SELECT * FROM ct_contract_item WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    CtContractItem selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
