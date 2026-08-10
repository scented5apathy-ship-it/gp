package com.genealogy.platform.services.genealogy.persistence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON encoder/decoder for the {@code branding} column.
 * Branding is a flat string map; we avoid pulling in a JSON
 * dependency for this column and rely on a hand-rolled
 * encoder/decoder that produces / consumes the exact bytes
 * PostgreSQL's {@code jsonb} cast expects.
 *
 * <p>This is NOT a general-purpose JSON utility — only the
 * branding shape ({@code Map<String,String>}) is supported.
 */
public final class BrandingJson {

    private BrandingJson() {
    }

    public static String encode(Map<String, String> branding) {
        if (branding == null || branding.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder(branding.size() * 16);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : branding.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            sb.append('"').append(escape(e.getValue() == null ? "" : e.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    public static Map<String, String> decode(String raw) {
        if (raw == null || raw.isBlank() || "{}".equals(raw.trim())) {
            return Map.of();
        }
        String body = raw.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String entry : splitTopLevel(body)) {
            int colon = findUnescapedColon(entry);
            if (colon < 0) {
                continue;
            }
            String k = unquote(entry.substring(0, colon).trim());
            String v = unquote(entry.substring(colon + 1).trim());
            out.put(k, v);
        }
        return Collections.unmodifiableMap(out);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i += 1) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
        }
        return s;
    }

    private static int findUnescapedColon(String s) {
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i += 1) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (c == ':' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }

    private static java.util.List<String> splitTopLevel(String body) {
        java.util.List<String> out = new java.util.ArrayList<>();
        boolean inQuotes = false;
        int depth = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i += 1) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == '{' || c == '[') {
                    depth += 1;
                } else if (c == '}' || c == ']') {
                    depth -= 1;
                } else if (c == ',' && depth == 0) {
                    out.add(body.substring(start, i));
                    start = i + 1;
                }
            }
        }
        if (start < body.length()) {
            out.add(body.substring(start));
        }
        return out;
    }
}
