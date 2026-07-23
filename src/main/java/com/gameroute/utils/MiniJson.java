package com.gameroute.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, correct JSON reader for the handful of responses (bug reports especially) whose
 * free-text fields -- description, logs, notes -- can legitimately contain quotes, newlines,
 * backslashes or anything else a user typed, which the codebase's usual regex-based field
 * extraction (used everywhere else for small, controlled values like IDs and role names) isn't
 * safe against. No external dependency; just enough of the JSON grammar to parse objects, arrays,
 * strings (with escapes), numbers, booleans and null.
 */
public final class MiniJson {

    private final String json;
    private int pos;

    private MiniJson(String json) {
        this.json = json;
    }

    /** Parses a JSON document into nested {@code Map}/{@code List}/{@code String}/{@code Double}/{@code Boolean}/{@code null}. */
    public static Object parse(String json) {
        MiniJson parser = new MiniJson(json);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private Object parseValue() {
        char c = json.charAt(pos);
        if (c == '{') {
            return parseObjectValue();
        }
        if (c == '[') {
            return parseArrayValue();
        }
        if (c == '"') {
            return parseStringValue();
        }
        if (c == 't' || c == 'f') {
            return parseBooleanValue();
        }
        if (c == 'n') {
            pos += 4; // "null"
            return null;
        }
        return parseNumberValue();
    }

    private Map<String, Object> parseObjectValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        pos++; // '{'
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = parseStringValue();
            skipWhitespace();
            pos++; // ':'
            skipWhitespace();
            result.put(key, parseValue());
            skipWhitespace();
            char c = json.charAt(pos++);
            if (c == '}') {
                break;
            }
            skipWhitespace();
        }
        return result;
    }

    private List<Object> parseArrayValue() {
        List<Object> result = new ArrayList<>();
        pos++; // '['
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            result.add(parseValue());
            skipWhitespace();
            char c = json.charAt(pos++);
            if (c == ']') {
                break;
            }
            skipWhitespace();
        }
        return result;
    }

    private String parseStringValue() {
        pos++; // opening '"'
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = json.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                char esc = json.charAt(pos++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        String hex = json.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Boolean parseBooleanValue() {
        if (json.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        pos += 5; // "false"
        return Boolean.FALSE;
    }

    private Double parseNumberValue() {
        int start = pos;
        while (pos < json.length() && "-+0123456789.eE".indexOf(json.charAt(pos)) >= 0) {
            pos++;
        }
        return Double.parseDouble(json.substring(start, pos));
    }

    private char peek() {
        return json.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }
}
