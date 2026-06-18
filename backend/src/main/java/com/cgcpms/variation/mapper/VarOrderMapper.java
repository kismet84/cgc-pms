package com.cgcpms.variation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.variation.entity.VarOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VarOrderMapper extends BaseMapper<VarOrder> {

    /**
     * 查询最新签证编号（含软删除记录，避免编号冲突）
     */
    @Select("SELECT var_code FROM var_order WHERE var_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY var_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}
