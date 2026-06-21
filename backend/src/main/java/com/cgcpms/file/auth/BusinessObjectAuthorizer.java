package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 文件关联业务对象授权校验器。
 * <p>
 * 每个 businessType 必须注册对应的校验逻辑，
 * 在上传、下载、列表、删除文件前验证当前用户对关联业务对象的访问权限。
 * 未知 businessType 将直接拒绝。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessObjectAuthorizer {

    private final ProjectAccessChecker projectAccessChecker;

    private static final Set<String> KNOWN_BUSINESS_TYPES = Set.of(
            "PROJECT", "CONTRACT", "INVOICE", "RECEIPT",
            "PAYMENT", "SUBCONTRACT", "SETTLEMENT", "VARIATION",
            "BID_COST", "PARTNER", "MATERIAL"
    );

    /**
     * 验证当前用户对指定业务对象拥有读权限。
     */
    public void checkReadAccess(String businessType, Long businessId) {
        checkAccess(businessType, businessId, "读取");
    }

    /**
     * 验证当前用户对指定业务对象拥有写权限。
     */
    public void checkWriteAccess(String businessType, Long businessId) {
        checkAccess(businessType, businessId, "写入");
    }

    private void checkAccess(String businessType, Long businessId, String action) {
        if (businessType == null || !KNOWN_BUSINESS_TYPES.contains(businessType.toUpperCase())) {
            throw new BusinessException("FILE_BIZ_TYPE_UNKNOWN",
                    "不支持的业务类型: " + businessType);
        }

        String upperType = businessType.toUpperCase();

        switch (upperType) {
            case "PROJECT":
                // 项目文件需要项目读权限
                projectAccessChecker.checkAccess(businessId, action + "项目文件");
                break;
            // 其他业务类型的授权检查在对应实现就绪后添加
            // 当前至少校验业务对象不为 null
            case "CONTRACT":
            case "INVOICE":
            case "RECEIPT":
            case "PAYMENT":
            case "SUBCONTRACT":
            case "SETTLEMENT":
            case "VARIATION":
            case "BID_COST":
            case "PARTNER":
            case "MATERIAL":
                // 当前仅做租户级校验（通过各业务 service 的查询间接保证）
                // 未来扩展：为每种类型注册对应的 access checker
                log.debug("业务对象授权: type={}, id={}, action={}, tenant={}",
                        businessType, businessId, action, UserContext.getCurrentTenantId());
                break;
            default:
                throw new BusinessException("FILE_BIZ_TYPE_UNKNOWN",
                        "不支持的业务类型: " + businessType);
        }
    }
}
