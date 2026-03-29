package com.codename1.impl;

import java.util.ArrayList;
import java.util.Locale;

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

    public static String format(String format, Object... args) {
        return format(Locale.getDefault(), format, args);
    }

    public static String format(Locale locale, String format, Object... args) {
        if (format == null) {
            throw new NullPointerException("format is null");
        }
        try {
            return java.lang.String.format(locale, format, args);
        } catch (Throwable ex) {
            return fallbackFormat(format, args);
        }
    }

    private static String fallbackFormat(String format, Object... args) {
        if (args == null || args.length == 0) {
            return format;
        }
        StringBuilder out = new StringBuilder();
        int argIndex = 0;
        int len = format.length();
        for (int i = 0; i < len; i++) {
            char ch = format.charAt(i);
            if (ch == '%' && i + 1 < len) {
                char spec = format.charAt(i + 1);
                if (spec == '%') {
                    out.append('%');
                    i++;
                    continue;
                }
                if (argIndex < args.length) {
                    out.append(String.valueOf(args[argIndex++]));
                    i++;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }
}
