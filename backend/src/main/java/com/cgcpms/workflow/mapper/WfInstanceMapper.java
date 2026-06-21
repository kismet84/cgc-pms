package com.cgcpms.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.workflow.entity.WfInstance;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WfInstanceMapper extends BaseMapper<WfInstance> {

    /**
     * Query all rows (including logically-deleted) for the given business key.
     * Uses raw SQL to bypass MyBatis-Plus {@code @TableLogic} filtering.
     * SUPER_ADMIN only — for admin data inspection/cleanup.
     */
    @Select("SELECT * FROM wf_instance WHERE business_type = #{businessType} AND business_id = #{businessId}")
    List<WfInstance> selectAllIncludingDeleted(@Param("businessType") String businessType,
                                                @Param("businessId") Long businessId);

    /**
     * Physically delete a row by ID (bypasses {@code @TableLogic} logical delete).
     * SUPER_ADMIN only — for admin cleanup and test data teardown.
     * Normal workflow operations use soft-delete or status transitions (WITHDRAWN, VOIDED).
     */
    @Delete("DELETE FROM wf_instance WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);

    /**
     * CAS update: atomically transition instance status from expectedStatus to newStatus.
     * Returns 1 if exactly one row matched, 0 if already changed by a concurrent operation.
     */
    @Update("UPDATE wf_instance SET instance_status = #{newStatus}, ended_at = #{endedAt} " +
            "WHERE id = #{instanceId} AND instance_status = #{expectedStatus} AND tenant_id = #{tenantId} AND deleted_flag = 0")
    int updateInstanceStatusWithCas(@Param("instanceId") Long instanceId,
                                    @Param("expectedStatus") String expectedStatus,
                                    @Param("newStatus") String newStatus,
                                    @Param("endedAt") java.time.LocalDateTime endedAt,
                                    @Param("tenantId") Long tenantId);

    /**
     * CAS ping: acquires row lock on the instance row without changing its status.
     * Returns 1 if the instance is still in the expected status, 0 otherwise.
     * Used by approve/transfer to serialize with concurrent withdraw.
     */
    @Update("UPDATE wf_instance SET updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{instanceId} AND instance_status = #{expectedStatus} AND deleted_flag = 0")
    int pingInstanceRunning(@Param("instanceId") Long instanceId,
                            @Param("expectedStatus") String expectedStatus);

    /**
     * CAS update for withdraw: atomically transition instance status ONLY IF no task
     * has been APPROVED or REJECTED (preventing concurrent approve from being overwritten).
     * Returns 1 if exactly one row matched, 0 if a concurrent approve already happened.
     */
    @Update("UPDATE wf_instance SET instance_status = #{newStatus}, ended_at = #{endedAt} " +
            "WHERE id = #{instanceId} AND instance_status = #{expectedStatus} " +
            "AND tenant_id = #{tenantId} AND deleted_flag = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM wf_task WHERE instance_id = #{instanceId} " +
            "AND task_status IN ('APPROVED', 'REJECTED') AND deleted_flag = 0)")
    int updateInstanceStatusWithCasNoApprovedTasks(@Param("instanceId") Long instanceId,
                                                    @Param("expectedStatus") String expectedStatus,
                                                    @Param("newStatus") String newStatus,
                                                    @Param("endedAt") java.time.LocalDateTime endedAt,
                                                    @Param("tenantId") Long tenantId);
}
