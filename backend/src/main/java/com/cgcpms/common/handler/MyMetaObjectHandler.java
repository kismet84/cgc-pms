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
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
        }
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId != null) {
            this.strictInsertFill(metaObject, "tenantId", Long.class, tenantId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
        }
    }
}
