package com.github.jefersonsantos06.t3wproductivity.util;

import java.util.ArrayList;
import java.util.List;

public final class TextParsingUtils {

    private TextParsingUtils() {
    }

    public static int findStringLiteralEnd(final String text, final int start) {
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            final var current = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    public static int findMatchingParenthesis(final String source, final int openParenIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = openParenIndex; i < source.length(); i++) {
            final char current = source.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (current == '\\') {
                    escaping = true;
                    continue;
                }
                if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static List<String> splitTopLevelArguments(final String argumentsText) {
        final var arguments = new ArrayList<String>();
        final var current = new StringBuilder();
        int parenthesesDepth = 0;
        int angleDepth = 0;
        int braceDepth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < argumentsText.length(); i++) {
            final char c = argumentsText.charAt(i);
            if (inString) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            switch (c) {
                case '"' -> {
                    inString = true;
                    current.append(c);
                }
                case '(' -> {
                    parenthesesDepth++;
                    current.append(c);
                }
                case ')' -> {
                    parenthesesDepth--;
                    current.append(c);
                }
                case '<' -> {
                    angleDepth++;
                    current.append(c);
                }
                case '>' -> {
                    angleDepth = Math.max(0, angleDepth - 1);
                    current.append(c);
                }
                case '{' -> {
                    braceDepth++;
                    current.append(c);
                }
                case '}' -> {
                    braceDepth = Math.max(0, braceDepth - 1);
                    current.append(c);
                }
                case ',' -> {
                    if (parenthesesDepth == 0 && angleDepth == 0 && braceDepth == 0) {
                        arguments.add(current.toString().trim());
                        current.setLength(0);
                    } else {
                        current.append(c);
                    }
                }
                default -> current.append(c);
            }
        }
        if (!current.isEmpty()) {
            arguments.add(current.toString().trim());
        }
        return arguments;
    }
}
