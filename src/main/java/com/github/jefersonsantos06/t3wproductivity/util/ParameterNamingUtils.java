package com.github.jefersonsantos06.t3wproductivity.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ParameterNamingUtils {

    private static final Pattern PLAIN_IDENTIFIER_PATTERN = Pattern.compile("^[a-z][A-Za-z0-9_]*$");
    private static final Pattern ENUM_ACCESSOR_PATTERN = Pattern.compile("([A-Z][A-Za-z0-9_]*)\\.([A-Z0-9_]+)\\.get[A-Za-z0-9_]+\\s*\\(");
    private static final Pattern VALUE_OF_PATTERN = Pattern.compile("\\bvalueOf\\(\\s*([a-z][A-Za-z0-9_]*)\\s*\\)");
    private static final Pattern GETTER_PATTERN = Pattern.compile("\\.(get|is)([A-Za-z0-9_]+)\\s*\\(");

    private ParameterNamingUtils() {
    }

    public static String extractExpressionPreferredName(final String expression) {
        final var trimmed = expression == null ? "" : expression.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        final var plainIdentifierMatcher = PLAIN_IDENTIFIER_PATTERN.matcher(trimmed);
        if (plainIdentifierMatcher.matches()) {
            return plainIdentifierMatcher.group();
        }

        final var enumMatcher = ENUM_ACCESSOR_PATTERN.matcher(trimmed);
        if (enumMatcher.find()) {
            return toLowerCamel(enumMatcher.group(1)) + toPascalCase(enumMatcher.group(2).toLowerCase(Locale.ROOT));
        }

        final var valueOfMatcher = VALUE_OF_PATTERN.matcher(trimmed);
        if (valueOfMatcher.find()) {
            return valueOfMatcher.group(1);
        }

        return null;
    }

    public static String extractGetterBasedName(final String expression) {
        final var trimmed = expression == null ? "" : expression.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        String latest = null;
        final var getterMatcher = GETTER_PATTERN.matcher(trimmed);
        while (getterMatcher.find()) {
            latest = getterMatcher.group(2);
        }
        if (latest != null) {
            return toLowerCamel(latest);
        }
        return null;
    }

    public static String ensureUniqueName(final String base, final Set<String> usedNames) {
        String name = base;
        int suffix = 2;
        while (usedNames.contains(name)) {
            name = base + suffix;
            suffix++;
        }
        return name;
    }

    public static String sanitizeParameterName(final String raw, final int fallbackIndex) {
        if (raw == null || raw.isBlank()) {
            return "param" + fallbackIndex;
        }
        final StringBuilder normalized = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                normalized.append(c);
            }
        }
        if (normalized.isEmpty()) {
            return "param" + fallbackIndex;
        }
        return toLowerCamel(normalized.toString());
    }

    public static String normalizedExpression(final String expression) {
        return expression == null ? "" : expression.replaceAll("\\s+", "");
    }

    public static String toLowerCamel(final String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.indexOf('_') >= 0) {
            return toCamelCaseFromUnderscore(value, false);
        }
        if (value.equals(value.toUpperCase(Locale.ROOT))) {
            return value.toLowerCase(Locale.ROOT);
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    public static String toPascalCase(final String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return toCamelCaseFromUnderscore(value, true);
    }

    public static String toCamelCaseFromUnderscore(final String value, final boolean capitalizeFirst) {
        final String[] parts = value.split("_+");
        final StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isBlank()) {
                continue;
            }
            final String lower = parts[i].toLowerCase(Locale.ROOT);
            if (i == 0 && !capitalizeFirst) {
                out.append(lower);
            } else {
                out.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
        }
        return out.toString();
    }
}
