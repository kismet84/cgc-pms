package com.cgcpms.common.util;

/**
 * 统一编号生成器的软删查询适配接口。
 */
public interface DeletedCodeSource {

    /**
     * 查询给定前缀下的最新编号（可选是否包含软删除记录），用于 includeDeleted=true 场景。
     *
     * @param prefix 编号前缀（包含日期等前缀）
     * @param tenantId 租户 ID
     * @return 当前前缀下按字典序最新的完整编号；无记录返回 null
     */
    String selectLastCodeByPrefix(String prefix, Long tenantId);
}
