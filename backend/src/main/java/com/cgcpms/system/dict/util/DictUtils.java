package com.cgcpms.system.dict.util;

import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.system.dict.vo.SysDictDataVO;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典工具类 - 提供静态方法访问字典数据
 * <p>
 * 使用示例：
 * <pre>
 *   // 获取状态标签
 *   String label = DictUtils.getLabelByValue("approval_status", "DRAFT");  // "草稿"
 *
 *   // 判断值是否有效
 *   boolean valid = DictUtils.isValidValue("contract_status", "PERFORMING");  // true
 *
 *   // 获取键值-标签映射
 *   Map&lt;String, String&gt; map = DictUtils.getValueLabelMap("approval_status");
 * </pre>
 */
public final class DictUtils {

    private DictUtils() {
    }

    /**
     * 根据字典编码获取字典数据列表（带缓存）
     *
     * @param dictCode 字典编码
     * @return 字典数据列表
     */
    public static List<SysDictDataVO> getDictDataByCode(String dictCode) {
        SysDictDataService service = ApplicationContextHolder.getBean(SysDictDataService.class);
        if (service == null) {
            return List.of();
        }
        return service.getByDictCodeCached(dictCode);
    }

    /**
     * 根据字典编码和键值获取标签
     *
     * @param dictCode  字典编码
     * @param dictValue 字典键值
     * @return 字典标签，未找到时返回 dictValue 本身
     */
    public static String getLabelByValue(String dictCode, String dictValue) {
        if (dictValue == null || dictValue.isEmpty()) {
            return dictValue;
        }
        return getDictDataByCode(dictCode).stream()
                .filter(item -> dictValue.equals(item.getDictValue()))
                .map(SysDictDataVO::getDictLabel)
                .findFirst()
                .orElse(dictValue);
    }

    /**
     * 根据字典编码获取键值-标签映射
     *
     * @param dictCode 字典编码
     * @return 键值 -> 标签 的映射
     */
    public static Map<String, String> getValueLabelMap(String dictCode) {
        return getDictDataByCode(dictCode).stream()
                .collect(Collectors.toMap(
                        SysDictDataVO::getDictValue,
                        SysDictDataVO::getDictLabel,
                        (v1, v2) -> v1
                ));
    }

    /**
     * 根据字典编码和键值判断是否有效
     *
     * @param dictCode  字典编码
     * @param dictValue 字典键值
     * @return 是否有效
     */
    public static boolean isValidValue(String dictCode, String dictValue) {
        if (dictValue == null || dictValue.isEmpty()) {
            return false;
        }
        return getDictDataByCode(dictCode).stream()
                .anyMatch(item -> dictValue.equals(item.getDictValue()));
    }
}
