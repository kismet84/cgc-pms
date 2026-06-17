package com.cgcpms.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.workflow.entity.WfInstance;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WfInstanceMapper extends BaseMapper<WfInstance> {

    /**
     * Query all rows (including logically-deleted) for the given business key.
     * Uses raw SQL to bypass MyBatis-Plus {@code @TableLogic} filtering.
     */
    @Select("SELECT * FROM wf_instance WHERE business_type = #{businessType} AND business_id = #{businessId}")
    List<WfInstance> selectAllIncludingDeleted(@Param("businessType") String businessType,
                                               @Param("businessId") Long businessId);

    /**
     * Physically delete a row by ID (bypasses {@code @TableLogic} logical delete).
     */
    @Delete("DELETE FROM wf_instance WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);
}
