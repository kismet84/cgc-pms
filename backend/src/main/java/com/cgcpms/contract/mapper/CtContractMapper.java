package com.cgcpms.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.contract.entity.CtContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CtContractMapper extends BaseMapper<CtContract> {

    @Select("SELECT * FROM ct_contract WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    CtContract selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 查询最新合同编号（含软删除记录，避免编号冲突）
     */
    @Select("SELECT contract_code FROM ct_contract WHERE contract_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY contract_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}
