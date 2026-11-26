package com.aimes.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 多单位换算工具类
 * 解析物料/产品的 unitConfig JSON，提供基本单位与辅助单位之间的换算
 * unitConfig 格式: {"baseUnit":"个","conversions":[{"unit":"kg","ratio":10}]}
 */
@Slf4j
public class UnitConversionUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 换算关系记录
     */
    public record UnitConversion(String baseUnit, String secondaryUnit, BigDecimal ratio) {}

    /**
     * 解析 unitConfig JSON，返回换算关系；无配置或解析失败返回 null
     */
    public static UnitConversion parse(String unitConfig) {
        if (unitConfig == null || unitConfig.isBlank()) return null;
        try {
            JsonNode root = MAPPER.readTree(unitConfig);
            String baseUnit = root.has("baseUnit") ? root.get("baseUnit").asText() : null;
            JsonNode conversions = root.get("conversions");
            if (baseUnit == null || conversions == null || !conversions.isArray() || conversions.isEmpty()) {
                return null;
            }
            JsonNode first = conversions.get(0);
            String secondaryUnit = first.has("unit") ? first.get("unit").asText() : null;
            BigDecimal ratio = first.has("ratio") ? new BigDecimal(first.get("ratio").asText()) : null;
            if (secondaryUnit == null || secondaryUnit.isBlank() || ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return new UnitConversion(baseUnit, secondaryUnit, ratio);
        } catch (Exception e) {
            log.warn("解析unitConfig失败: {}", unitConfig, e);
            return null;
        }
    }

    /**
     * 将基本单位数量换算为辅助单位数量
     * @return 辅助数量，无换算配置返回 null
     */
    public static BigDecimal toSecondary(BigDecimal baseQty, String unitConfig) {
        if (baseQty == null) return null;
        UnitConversion conv = parse(unitConfig);
        if (conv == null) return null;
        return baseQty.multiply(conv.ratio()).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 获取辅助单位名称，无换算配置返回 null
     */
    public static String getSecondaryUnit(String unitConfig) {
        UnitConversion conv = parse(unitConfig);
        return conv != null ? conv.secondaryUnit() : null;
    }

    /**
     * 获取换算比率，无换算配置返回 null
     */
    public static BigDecimal getRatio(String unitConfig) {
        UnitConversion conv = parse(unitConfig);
        return conv != null ? conv.ratio() : null;
    }
}
