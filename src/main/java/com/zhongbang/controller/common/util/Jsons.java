package com.zhongbang.controller.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

/**
 * 少量字段在表里存成一段文本：凭证地址是 JSON 数组，标签和技能是逗号分隔。
 * 读不出来时当成空列表，避免一列脏数据把整个订单详情打成 500。
 */
public final class Jsons {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Jsons() {
    }

    public static String writeStrings(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            return "[]";
        }
    }

    public static List<String> readStrings(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String text = raw.trim();
        if (text.startsWith("[")) {
            try {
                return MAPPER.readValue(text, new TypeReference<List<String>>() {
                });
            } catch (Exception ex) {
                return List.of();
            }
        }
        return splitComma(text);
    }

    public static List<String> splitComma(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
