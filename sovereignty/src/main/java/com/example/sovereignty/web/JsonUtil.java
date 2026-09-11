package com.example.sovereignty.web;

import java.util.List;
import java.util.Map;

/**
 * Простой JSON-сериализатор без внешних зависимостей.
 * Поддерживает String, Number, Boolean, List, Map, null.
 */
public class JsonUtil {

    public static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    public static String toJson(Object obj) {
        StringBuilder sb = new StringBuilder();
        write(sb, obj);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object obj) {
        if (obj == null) { sb.append("null"); return; }
        if (obj instanceof String s) { sb.append(escape(s)); return; }
        if (obj instanceof Number || obj instanceof Boolean) { sb.append(obj.toString()); return; }
        if (obj instanceof Map<?, ?> map) {
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(escape(String.valueOf(e.getKey()))).append(":");
                write(sb, e.getValue());
            }
            sb.append("}");
            return;
        }
        if (obj instanceof List<?> list) {
            sb.append("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                first = false;
                write(sb, item);
            }
            sb.append("]");
            return;
        }
        // fallback
        sb.append(escape(obj.toString()));
    }
}
