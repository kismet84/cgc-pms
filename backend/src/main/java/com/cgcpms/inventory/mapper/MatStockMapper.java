package com.cgcpms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.inventory.entity.MatStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatStockMapper extends BaseMapper<MatStock> {

    @Select("SELECT * FROM mat_stock WHERE id = #{id} AND tenant_id = #{tenantId} "
            + "AND deleted_flag = 0 FOR UPDATE")
    MatStock selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
