package com.cgcpms.workflow.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.workflow.entity.WfTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WfTaskMapper extends BaseMapper<WfTask> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM wf_task WHERE id = #{id} AND deleted_flag = 0")
    WfTask selectByIdIgnoringTenant(@Param("id") Long id);

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
