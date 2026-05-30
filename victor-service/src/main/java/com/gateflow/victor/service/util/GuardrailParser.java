package com.gateflow.victor.service.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 护栏指标 JSON 解析工具 — 从实验配置中提取护栏指标名称列表。
 * 支持两种格式：["metric1","metric2"] 和 [{"name":"metric1"},{"name":"metric2"}]
 */
@Slf4j
public final class GuardrailParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GuardrailParser() {}

    /**
     * @return 指标名称列表，json 为空或解析失败时返回 null
     */
    public static List<String> parse(String guardrailMetricsJson) {
        if (guardrailMetricsJson == null || guardrailMetricsJson.isBlank()) {
            return null;
        }
        try {
            List<?> raw = MAPPER.readValue(guardrailMetricsJson, new TypeReference<List<?>>() {});
            List<String> names = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof String s) {
                    names.add(s);
                } else if (item instanceof Map<?, ?> m && m.containsKey("name")) {
                    names.add(m.get("name").toString());
                }
            }
            return names.isEmpty() ? null : names;
        } catch (Exception e) {
            log.warn("Failed to parse guardrail_metrics JSON: {}", guardrailMetricsJson, e);
            return null;
        }
    }
}
