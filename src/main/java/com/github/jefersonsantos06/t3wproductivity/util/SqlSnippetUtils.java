package com.github.jefersonsantos06.t3wproductivity.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SqlSnippetUtils {

    private static final Pattern SQL_IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern QUOTED_FRAGMENTS_PATTERN = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern INSERT_VALUES_LINE_PATTERN = Pattern.compile("(?i)^(\\s*VALUES\\s*\\()(.*)(\\)\\s*)$");

    private SqlSnippetUtils() {
    }

    public static String inferHintFromSqlTokens(final List<String> tokens) {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            final String token = tokens.get(i);
            if ("=".equals(token) && i > 0) {
                final String left = stripQualifier(tokens.get(i - 1));
                if (isSqlIdentifier(left)) {
                    return ParameterNamingUtils.toLowerCamel(left);
                }
            }
        }
        for (int i = tokens.size() - 1; i >= 0; i--) {
            final String token = stripQualifier(tokens.get(i));
            if (isSqlIdentifier(token)) {
                return ParameterNamingUtils.toLowerCamel(token);
            }
        }
        return null;
    }

    public static boolean isLowSignalSqlHint(final String hint) {
        if (hint == null || hint.isBlank()) {
            return true;
        }
        final var lower = hint.toLowerCase(Locale.ROOT);
        return lower.equals("values")
                || lower.equals("value")
                || lower.equals("where")
                || lower.equals("set")
                || lower.equals("and")
                || lower.equals("or");
    }

    public static int countSqlPlaceholdersUntil(final String source, final int endExclusive) {
        int count = 0;
        boolean inString = false;
        boolean escaped = false;
        final int limit = Math.min(source.length(), Math.max(0, endExclusive));
        for (int i = 0; i < limit; i++) {
            final char current = source.charAt(i);
            if (!inString) {
                if (current == '"') {
                    inString = true;
                }
                continue;
            }
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = false;
                continue;
            }
            if (current == '?') {
                count++;
            }
        }
        return count;
    }

    public static List<String> splitCommaSeparated(final String text) {
        final List<String> items = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return items;
        }
        for (final var part : text.split(",")) {
            final var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    public static String stripQualifier(final String token) {
        final int dotIndex = token.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex + 1 < token.length()) {
            return token.substring(dotIndex + 1);
        }
        return token;
    }

    public static boolean isSqlIdentifier(final String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return SQL_IDENTIFIER_PATTERN.matcher(token).matches();
    }

    public static List<String> extractQuotedFragments(final String text) {
        final var fragments = new ArrayList<String>();
        final var matcher = QUOTED_FRAGMENTS_PATTERN.matcher(text);
        while (matcher.find()) {
            String fragment = matcher.group(1);
            fragment = fragment.replace("\\\"", "\"").replace("\\\\", "\\");
            fragments.add(fragment);
        }
        return fragments;
    }

    public static String normalizeInsertValuesLine(final String line, final boolean insertStatement) {
        if (!insertStatement) {
            return line;
        }
        final var matcher = INSERT_VALUES_LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return line;
        }
        final var valuesChunk = matcher.group(2).trim();
        if (!valuesChunk.contains(":")) {
            return line;
        }
        final var items = splitCommaSeparated(valuesChunk);
        if (items.isEmpty()) {
            return line;
        }
        final var normalizedItems = new ArrayList<String>(items.size());
        for (final var item : items) {
            normalizedItems.add(item.trim().replaceFirst("^:", ""));
        }
        return matcher.group(1) + String.join(", ", normalizedItems) + matcher.group(3);
    }
}
