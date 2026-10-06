package labchat.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal zero-dependency JSON parser and serializer for Lab Chat.
 * Handles objects, arrays, strings with escapes, numbers, booleans, and nulls.
 */
public final class JsonUtil {

    private JsonUtil() {}

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String success(String dataJson) {
        if (dataJson == null || dataJson.isBlank()) {
            return "{\"success\":true,\"data\":{}}";
        }
        return "{\"success\":true,\"data\":" + dataJson + "}";
    }

    public static String error(String code, String message) {
        return "{\"success\":false,\"error\":{\"code\":\"" + escape(code) + "\",\"message\":\"" + escape(message) + "\"}}";
    }

    public static Object parse(String json) {
        if (json == null) return null;
        String trimmed = json.trim();
        if (trimmed.isEmpty()) return null;
        int[] index = new int[]{0};
        return parseValue(trimmed, index);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object val = parse(json);
        if (val instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    public static String getString(Map<String, Object> map, String key, String defaultVal) {
        if (map == null) return defaultVal;
        Object val = map.get(key);
        if (val == null) return defaultVal;
        return val.toString();
    }

    public static Boolean getBoolean(Map<String, Object> map, String key, Boolean defaultVal) {
        if (map == null) return defaultVal;
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultVal;
    }

    public static Integer getInteger(Map<String, Object> map, String key, Integer defaultVal) {
        if (map == null) return defaultVal;
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return defaultVal;
    }

    public static List<String> getStringList(Map<String, Object> map, String key) {
        List<String> result = new ArrayList<>();
        if (map == null) return result;
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) result.add(item.toString());
            }
        }
        return result;
    }

    private static Object parseValue(String s, int[] idx) {
        skipWhitespace(s, idx);
        if (idx[0] >= s.length()) return null;
        char c = s.charAt(idx[0]);

        if (c == '{') return parseObj(s, idx);
        if (c == '[') return parseArr(s, idx);
        if (c == '"') return parseStr(s, idx);
        if (c == 't' || c == 'f') return parseBool(s, idx);
        if (c == 'n') return parseNull(s, idx);
        if (c == '-' || Character.isDigit(c)) return parseNum(s, idx);
        return null;
    }

    private static Map<String, Object> parseObj(String s, int[] idx) {
        Map<String, Object> map = new LinkedHashMap<>();
        idx[0]++; // skip '{'
        skipWhitespace(s, idx);
        if (idx[0] < s.length() && s.charAt(idx[0]) == '}') {
            idx[0]++;
            return map;
        }

        while (idx[0] < s.length()) {
            skipWhitespace(s, idx);
            if (idx[0] >= s.length() || s.charAt(idx[0]) != '"') break;
            String key = parseStr(s, idx);
            skipWhitespace(s, idx);
            if (idx[0] < s.length() && s.charAt(idx[0]) == ':') {
                idx[0]++;
            }
            skipWhitespace(s, idx);
            Object value = parseValue(s, idx);
            map.put(key, value);

            skipWhitespace(s, idx);
            if (idx[0] < s.length() && s.charAt(idx[0]) == ',') {
                idx[0]++;
            } else if (idx[0] < s.length() && s.charAt(idx[0]) == '}') {
                idx[0]++;
                break;
            } else {
                break;
            }
        }
        return map;
    }

    private static List<Object> parseArr(String s, int[] idx) {
        List<Object> list = new ArrayList<>();
        idx[0]++; // skip '['
        skipWhitespace(s, idx);
        if (idx[0] < s.length() && s.charAt(idx[0]) == ']') {
            idx[0]++;
            return list;
        }

        while (idx[0] < s.length()) {
            skipWhitespace(s, idx);
            Object value = parseValue(s, idx);
            list.add(value);
            skipWhitespace(s, idx);
            if (idx[0] < s.length() && s.charAt(idx[0]) == ',') {
                idx[0]++;
            } else if (idx[0] < s.length() && s.charAt(idx[0]) == ']') {
                idx[0]++;
                break;
            } else {
                break;
            }
        }
        return list;
    }

    private static String parseStr(String s, int[] idx) {
        idx[0]++; // skip '"'
        StringBuilder sb = new StringBuilder();
        while (idx[0] < s.length()) {
            char c = s.charAt(idx[0]++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\' && idx[0] < s.length()) {
                char next = s.charAt(idx[0]++);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (idx[0] + 4 <= s.length()) {
                            String hex = s.substring(idx[0], idx[0] + 4);
                            idx[0] += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (Exception ignored) {}
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Boolean parseBool(String s, int[] idx) {
        if (s.startsWith("true", idx[0])) {
            idx[0] += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", idx[0])) {
            idx[0] += 5;
            return Boolean.FALSE;
        }
        return null;
    }

    private static Object parseNull(String s, int[] idx) {
        if (s.startsWith("null", idx[0])) {
            idx[0] += 4;
        }
        return null;
    }

    private static Number parseNum(String s, int[] idx) {
        int start = idx[0];
        if (s.charAt(idx[0]) == '-') idx[0]++;
        while (idx[0] < s.length() && (Character.isDigit(s.charAt(idx[0])) || s.charAt(idx[0]) == '.' || s.charAt(idx[0]) == 'e' || s.charAt(idx[0]) == 'E' || s.charAt(idx[0]) == '+')) {
            idx[0]++;
        }
        String numStr = s.substring(start, idx[0]);
        if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
            try { return Double.parseDouble(numStr); } catch (Exception ignored) {}
        } else {
            try { return Long.parseLong(numStr); } catch (Exception ignored) {}
        }
        return 0;
    }

    private static void skipWhitespace(String s, int[] idx) {
        while (idx[0] < s.length() && Character.isWhitespace(s.charAt(idx[0]))) {
            idx[0]++;
        }
    }
}
