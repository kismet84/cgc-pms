package com.cgcpms.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.cgcpms.auth.context.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        // Support entities with Java field named createdTime/updatedTime (e.g. CtContractChange, CostTarget)
        // mapped via @TableField("created_at") / @TableField("updated_at")
        this.strictInsertFill(metaObject, "createdTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedTime", LocalDateTime.class, now);
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            this.setFieldValByName("createdBy", userId, metaObject);
            this.setFieldValByName("updatedBy", userId, metaObject);
        }
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId != null && this.getFieldValByName("tenantId", metaObject) == null) {
            this.setFieldValByName("tenantId", tenantId, metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updatedTime", LocalDateTime.class, now);
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
        }
    }
}
