package com.cgcpms.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.contract.entity.CtContractChange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CtContractChangeMapper extends BaseMapper<CtContractChange> {

    /**
     * 查询最新变更编号（含软删除记录，避免编号冲突）
     */
    @Select("SELECT change_code FROM ct_contract_change WHERE change_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY change_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}
