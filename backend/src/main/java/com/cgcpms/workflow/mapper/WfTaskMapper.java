package com.cgcpms.workflow.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.workflow.entity.WfTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WfTaskMapper extends BaseMapper<WfTask> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id,tenant_id,instance_id,node_instance_id,business_type,business_id,approver_id,approver_name," +
            "task_status,round_no,task_version,received_at,handled_at,action_type,comment,created_by,created_at," +
            "updated_by,updated_at,deleted_flag,remark FROM wf_task WHERE id = #{id} AND deleted_flag = 0")
    WfTask selectByIdIgnoringTenant(@Param("id") Long id);

    @Select("SELECT id,tenant_id,instance_id,node_instance_id,business_type,business_id,approver_id,approver_name," +
            "task_status,round_no,task_version,received_at,handled_at,action_type,comment,created_by,created_at," +
            "updated_by,updated_at,deleted_flag,remark FROM wf_task WHERE id=#{id} AND tenant_id=#{tenantId} " +
            "AND deleted_flag=0 FOR UPDATE")
    WfTask selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT id FROM wf_task WHERE tenant_id=#{tenantId} AND instance_id=#{instanceId} " +
            "AND approver_id=#{userId} AND round_no=#{roundNo} AND task_status='APPROVED' " +
            "AND deleted_flag=0 FOR UPDATE")
    List<Long> selectApprovedIdsForUpdate(@Param("tenantId") Long tenantId,
                                           @Param("instanceId") Long instanceId,
                                           @Param("userId") Long userId,
                                           @Param("roundNo") Integer roundNo);

    @Select("SELECT id FROM wf_task WHERE tenant_id=#{tenantId} AND node_instance_id=#{nodeInstanceId} " +
            "AND task_status='PENDING' AND deleted_flag=0 FOR UPDATE")
    List<Long> selectPendingIdsForUpdate(@Param("tenantId") Long tenantId,
                                          @Param("nodeInstanceId") Long nodeInstanceId);

    @Select("SELECT id FROM wf_task WHERE tenant_id=#{tenantId} AND node_instance_id=#{nodeInstanceId} " +
            "AND approver_id=#{userId} AND task_status='PENDING' AND deleted_flag=0 FOR UPDATE")
    List<Long> selectPendingApproverIdsForUpdate(@Param("tenantId") Long tenantId,
                                                  @Param("nodeInstanceId") Long nodeInstanceId,
                                                  @Param("userId") Long userId);

    /**
     * CAS update: atomically transition task status from expectedStatus to newStatus,
     * bumping task_version.  Returns 1 if exactly one row matched (expectedStatus + expectedVersion),
     * 0 if another concurrent operation already changed the task.
     */
    @Update("UPDATE wf_task SET task_status = #{newStatus}, task_version = task_version + 1, " +
            "action_type = #{actionType}, comment = #{comment}, handled_at = #{handledAt} " +
            "WHERE id = #{taskId} AND task_status = #{expectedStatus} AND task_version = #{expectedVersion} AND tenant_id = #{tenantId} AND deleted_flag = 0")
    int updateTaskStatusWithCas(@Param("taskId") Long taskId,
                                @Param("expectedStatus") String expectedStatus,
                                @Param("expectedVersion") Integer expectedVersion,
                                @Param("newStatus") String newStatus,
                                @Param("actionType") String actionType,
                                @Param("comment") String comment,
                                @Param("handledAt") LocalDateTime handledAt,
                                @Param("tenantId") Long tenantId);
}
