package com.aimes.common.utils;

import com.aimes.modules.master.entity.BizMaterial;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.util.StringUtils;

/**
 * 规格(spec)字段查询工具
 * <p>
 * 数据库中物料 spec 通常以 JSON 字符串存储,例如:
 *   [{"name":"口径","value":"15"},{"name":"压力等级","value":"300"},{"name":"材质","value":"NBR"}]
 * <p>
 * 搜索语义:
 *   1) 单值搜索:"口径" / "NBR" / "300" / "Ø15mm" → 单 token 在 JSON 字符串里 LIKE 匹配,任一字段含有就命中
 *   2) 复合搜索:"压力等级:300 材质:NBR" → 按空白/冒号拆 token,**每个 token 都要命中**(AND)
 *   注意:JSON 里既有 name(口径/压力等级/材质) 也有 value(15/300/NBR),
 *        LIKE 在 JSON 字符串上能同时匹配 name 关键字和 value 关键字
 */
public class SpecQueryUtil {

    /**
     * 把 spec 输入应用到任意实体的查询 wrapper(指定 spec 列访问器)
     * 多 token 之间用 AND
     */
    public static <T> void apply(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> column, String spec) {
        if (!StringUtils.hasText(spec)) return;
        for (String t : tokenize(spec)) {
            wrapper.like(column, t);
        }
    }

    /**
     * 专门用于 BizMaterial.spec 的查询(只搜 spec 字段,不污染名称)
     */
    public static void applyToBizMaterial(LambdaQueryWrapper<BizMaterial> wrapper, String spec) {
        apply(wrapper, BizMaterial::getSpec, spec);
    }

    /**
     * 拆 token:支持英文空格 / 中文全角空格 / 中英文冒号 / 顿号
     */
    public static String[] tokenize(String spec) {
        if (!StringUtils.hasText(spec)) return new String[0];
        // 半角空格 / 制表 / 换行 / 全角空格(　) / 中英文冒号 / 中文顿号
        String[] raw = spec.trim().split("[\\s\\u3000:::、]+");
        return java.util.Arrays.stream(raw)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }
}
