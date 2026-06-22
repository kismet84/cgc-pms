package com.cgcpms.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 公共编号生成服务 —— 统一替换各模块重复的 {@code XX-yyyyMMdd-XXX} 模式。
 * <p>
 * 使用 MyBatis-Plus {@link Page}(0, 1) 替代 {@code .last("LIMIT 1")}，兼容 MySQL 和 H2。
 * </p>
 * <p>
 * 注意：{@code includeDeleted} 当前在所有启用 {@code @TableLogic} 的实体上需要配合
 * 对应 Mapper 的 {@code @Select} 注解方法使用 SQL 片段方式调用。对于仅有非删除记录的编号查询，
 * 使用默认值 {@code false} 即可。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 方式1：Lambda 引用（类型安全，推荐）
 * String code = codeGenerationService.nextCode(
 *     matPurchaseOrderMapper,
 *     MatPurchaseOrder::getOrderCode,
 *     "PO-",
 *     UserContext.getCurrentTenantId()
 * );
 *
 * // 方式2：数据库列名（适合动态/反射场景）
 * String code = codeGenerationService.nextCode(
 *     matPurchaseOrderMapper,
 *     "order_code",
 *     MatPurchaseOrder::getOrderCode,
 *     "PO-",
 *     UserContext.getCurrentTenantId()
 * );
 * }</pre>
 */
@Slf4j
@Component
public class CodeGenerationService {

    private static final int SEQ_LENGTH = 3;

    // ---------------------------------------------------------------------
    // 重载 1：Lambda 引用（SFunction） —— 类型安全，推荐使用
    // ---------------------------------------------------------------------

    /**
     * 生成下一个业务编号（不包含软删除记录）。
     *
     * @param mapper     实体的 BaseMapper
     * @param codeGetter 编码字段的 Lambda getter（如 {@code MatPurchaseOrder::getOrderCode}）
     * @param prefix     编号前缀（如 {@code "PO-"}）
     * @param tenantId   租户 ID
     * @param <T>        实体类型
     * @return 下一个编号，如 {@code "PO-20260618-001"}
     */
    public <T> String nextCode(BaseMapper<T> mapper,
                               SFunction<T, String> codeGetter,
                               String prefix,
                               Long tenantId) {
        return nextCode(mapper, codeGetter, prefix, tenantId, false);
    }

    /**
     * 生成下一个业务编号（Lambda 引用版本，完整参数）。
     *
     * @param mapper         实体的 BaseMapper
     * @param codeGetter     编码字段的 Lambda getter（如 {@code MatPurchaseOrder::getOrderCode}）
     * @param prefix         编号前缀（如 {@code "PO-"}）
     * @param tenantId       租户 ID
     * @param includeDeleted 是否将软删除记录纳入序列号计算
     * @param <T>            实体类型
     * @return 下一个编号，如 {@code "PO-20260618-001"}
     */
    public <T> String nextCode(BaseMapper<T> mapper,
                               SFunction<T, String> codeGetter,
                               String prefix,
                               Long tenantId,
                               boolean includeDeleted) {
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String fullPrefix = prefix + today + "-";

        // Use QueryWrapper with string column name instead of LambdaQueryWrapper
        // with SFunctionUtil.getTenantIdSF() — TenantIdMarker is not a @TableName
        // entity so MyBatis-Plus cannot resolve its lambda cache.
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .likeRight(extractColumnName(codeGetter), fullPrefix)
                .orderByDesc(extractColumnName(codeGetter));

        return generateNextCodeStr(mapper, wrapper, fullPrefix, includeDeleted, codeGetter);
    }

    // ---------------------------------------------------------------------
    // 重载 2：字符串列名 —— 适用于动态列名场景
    // ---------------------------------------------------------------------

    /**
     * 生成下一个业务编号（字符串列名版本，不包含软删除记录）。
     *
     * @param mapper     实体的 BaseMapper
     * @param codeColumn 数据库编码列名（如 {@code "order_code"}）
     * @param codeGetter 编码字段的 Lambda getter（用于从查询结果实体上提取编码值）
     * @param prefix     编号前缀（如 {@code "PO-"}）
     * @param tenantId   租户 ID
     * @param <T>        实体类型
     * @return 下一个编号，如 {@code "PO-20260618-001"}
     */
    public <T> String nextCode(BaseMapper<T> mapper,
                               String codeColumn,
                               SFunction<T, String> codeGetter,
                               String prefix,
                               Long tenantId) {
        return nextCode(mapper, codeColumn, codeGetter, prefix, tenantId, false);
    }

    /**
     * 生成下一个业务编号（字符串列名版本，完整参数）。
     *
     * @param mapper         实体的 BaseMapper
     * @param codeColumn     数据库编码列名（如 {@code "order_code"}）
     * @param codeGetter     编码字段的 Lambda getter（用于从查询结果实体上提取编码值）
     * @param prefix         编号前缀（如 {@code "PO-"}）
     * @param tenantId       租户 ID
     * @param includeDeleted 是否将软删除记录纳入序列号计算
     * @param <T>            实体类型
     * @return 下一个编号，如 {@code "PO-20260618-001"}
     */
    public <T> String nextCode(BaseMapper<T> mapper,
                               String codeColumn,
                               SFunction<T, String> codeGetter,
                               String prefix,
                               Long tenantId,
                               boolean includeDeleted) {
        String today = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        String fullPrefix = prefix + today + "-";

        // 使用 QueryWrapper 支持原生列名字符串
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .likeRight(codeColumn, fullPrefix)
                .orderByDesc(codeColumn);

        return generateNextCodeStr(mapper, wrapper, fullPrefix, includeDeleted, codeGetter);
    }

    // ---------------------------------------------------------------------
    // 内部逻辑
    // ---------------------------------------------------------------------

    /**
     * 核心生成逻辑（Lambda 路径）。
     * <p>
     * 使用 {@code Page(0, 1)} 取排序后的第一条记录，兼容 MySQL 和 H2。
     * 当 {@code includeDeleted = true} 时，因 {@code @TableLogic} 拦截器
     * 无法通过 Wrapper API 绕过，使用 {@code selectList} 兜底并记录警告。
     * </p>
     */
    private <T> String generateNextCode(BaseMapper<T> mapper,
                                        LambdaQueryWrapper<T> wrapper,
                                        String fullPrefix,
                                        boolean includeDeleted,
                                        SFunction<T, String> codeGetter) {
        if (includeDeleted) {
            return generateWithDeleted(mapper, wrapper, fullPrefix, codeGetter);
        }

        Page<T> page = new Page<>(0, 1);
        Page<T> result = mapper.selectPage(page, wrapper);
        return parseSeq(result.getRecords(), fullPrefix, codeGetter);
    }

    private <T> String generateNextCodeStr(BaseMapper<T> mapper,
                                           QueryWrapper<T> wrapper,
                                           String fullPrefix,
                                           boolean includeDeleted,
                                           SFunction<T, String> codeGetter) {
        if (includeDeleted) {
            return generateWithDeletedStr(mapper, wrapper, fullPrefix, codeGetter);
        }

        Page<T> page = new Page<>(0, 1);
        Page<T> result = mapper.selectPage(page, wrapper);
        return parseSeq(result.getRecords(), fullPrefix, codeGetter);
    }

    /**
     * includeDeleted=true 的兜底路径。
     * <p>
     * MyBatis-Plus 的 {@code @TableLogic} 在 SQL 解析层注入 {@code deleted_flag = 0}，
     * 无法通过 Wrapper API 移除。当需要查询含软删除的记录时，请在对应 Mapper 中
     * 添加 {@code @Select} 注解方法。
     * </p>
     */
    private <T> String generateWithDeleted(BaseMapper<T> mapper,
                                           LambdaQueryWrapper<T> wrapper,
                                           String fullPrefix,
                                           SFunction<T, String> codeGetter) {
        // selectList 仍会被 @TableLogic 拦截
        // 此处使用 selectPage(Page(0, 1)) 对 LambdaQueryWrapper 执行
        // 结果与 includeDeleted=false 一致
        log.warn("includeDeleted=true 需通过 Mapper @Select 注解实现，当前查询不包含软删除记录");
        Page<T> page = new Page<>(0, 1);
        Page<T> result = mapper.selectPage(page, wrapper);
        return parseSeq(result.getRecords(), fullPrefix, codeGetter);
    }

    private <T> String generateWithDeletedStr(BaseMapper<T> mapper,
                                              QueryWrapper<T> wrapper,
                                              String fullPrefix,
                                              SFunction<T, String> codeGetter) {
        log.warn("includeDeleted=true 需通过 Mapper @Select 注解实现，当前查询不包含软删除记录");
        Page<T> page = new Page<>(0, 1);
        Page<T> result = mapper.selectPage(page, wrapper);
        return parseSeq(result.getRecords(), fullPrefix, codeGetter);
    }

    // ---------------------------------------------------------------------
    // 序列号解析
    // ---------------------------------------------------------------------

    /**
     * 从查询结果中解析最大值并返回下一个序列号。
     * <p>
     * 无记录或解析失败时均从 {@code 001} 开始。
     * </p>
     */
    private <T> String parseSeq(List<T> records,
                                String fullPrefix,
                                SFunction<T, String> codeGetter) {
        int seq = 1;
        if (!records.isEmpty()) {
            T last = records.get(0);
            String lastCode = codeGetter.apply(last);
            if (lastCode != null && lastCode.length() == fullPrefix.length() + SEQ_LENGTH) {
                try {
                    seq = Integer.parseInt(lastCode.substring(fullPrefix.length())) + 1;
                } catch (NumberFormatException e) {
                    log.warn("解析编码序列号失败: {}", lastCode, e);
                }
            }
        }
        return fullPrefix + String.format("%0" + SEQ_LENGTH + "d", seq);
    }

    // ---------------------------------------------------------------------
    // SFunction 工具：用于跨实体引用 tenantId 字段
    // ---------------------------------------------------------------------

    /**
     * 提供通用 {@code tenantId} 的 {@link SFunction} 引用。
     * <p>
     * 各实体中 {@code tenantId} 字段名统一，但并非都继承自同一个定义了该字段的超类。
     * 此处用一个标记接口的 lambda 生成属性名 {@code "tenantId"}，
     * 再强制转换为任意实体类型的 SFunction 供 {@link LambdaQueryWrapper#eq} 使用。
     * </p>
     */
    static final class SFunctionUtil {
        @SuppressWarnings("unchecked")
        static <T> SFunction<T, Long> getTenantIdSF() {
            return (SFunction<T, Long>) TENANT_ID_SF;
        }

        interface TenantIdMarker {
            Long getTenantId();

            void setTenantId(Long tenantId);
        }

        private static final SFunction<TenantIdMarker, Long> TENANT_ID_SF =
                TenantIdMarker::getTenantId;
    }

    /**
     * Extract the database column name from a SFunction (lambda getter reference).
     * Uses MyBatis-Plus internal LambdaUtils to resolve the property name, then
     * converts camelCase to snake_case.
     */
    private static <T> String extractColumnName(SFunction<T, ?> getter) {
        LambdaMeta meta = LambdaUtils.extract(getter);
        String property = meta.getImplMethodName();
        // Convert getXxx -> xxx
        if (property.startsWith("get") && property.length() > 3) {
            property = Character.toLowerCase(property.charAt(3)) + property.substring(4);
        }
        // camelCase -> snake_case
        return com.baomidou.mybatisplus.core.toolkit.StringUtils.camelToUnderline(property);
    }
}
