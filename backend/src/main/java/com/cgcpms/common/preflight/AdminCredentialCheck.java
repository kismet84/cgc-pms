package com.cgcpms.common.preflight;

import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 生产环境启动前置检查：拒绝已知固定管理员凭据。
 * <p>
 * 如果数据库中仍存在 admin / admin123 的已知哈希，应用将拒绝启动。
 * 运维必须在生产部署前通过一次性安全注入流程创建管理员，
 * 或轮换已部署环境的默认口令。
 * </p>
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class AdminCredentialCheck {

    /** 已知固定密码的 BCrypt 哈希 (admin123) */
    private static final String KNOWN_DEFAULT_HASH =
            "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    private final SysUserMapper sysUserMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "admin")
                .eq(SysUser::getPassword, KNOWN_DEFAULT_HASH));
        if (count > 0) {
            log.error("=".repeat(72));
            log.error("安全阻断：检测到固定管理员凭据 (admin/admin123)");
            log.error("生产环境禁止使用默认管理员口令启动，阻断成功。");
            log.error("请立即在生产系统中重置 admin 账号口令，并审计初始化凭据流转。");
            log.error("=".repeat(72));
            throw new IllegalStateException(
                    "生产环境拒绝启动：检测到固定管理员凭据。请参阅日志中的处置步骤。");
        }
        log.info("管理员凭据检查通过：未检测到默认凭据");
    }
}
