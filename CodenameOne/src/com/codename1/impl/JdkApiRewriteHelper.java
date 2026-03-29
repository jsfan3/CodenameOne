package com.codename1.impl;

/**
 * Bridge methods used by bytecode rewrite rules for JDK APIs that are risky/unsupported on some targets.
 */
public final class JdkApiRewriteHelper {
    private JdkApiRewriteHelper() {
    }

    public static String[] split(String source, String regex) {
        return split(source, regex, 0);
    }

    public static String[] split(String source, String regex, int limit) {
        if (source == null) {
            throw new NullPointerException("source is null");
        }
        if (regex == null) {
            throw new NullPointerException("regex is null");
        }
        try {
            return java.util.regex.Pattern.compile(regex).split(source, limit);
        } catch (Throwable ex) {
            // Fallback for incomplete regex support on some legacy targets.
            java.util.List<String> out = com.codename1.util.StringUtil.tokenize(source, regex);
            return out.toArray(new String[out.size()]);
        }
    }
}
