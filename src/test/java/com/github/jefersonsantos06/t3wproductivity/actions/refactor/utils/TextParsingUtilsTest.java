package com.github.jefersonsantos06.t3wproductivity.actions.refactor.utils;

import com.github.jefersonsantos06.t3wproductivity.util.TextParsingUtils;
import junit.framework.TestCase;

import java.util.List;

public class TextParsingUtilsTest extends TestCase {

    public void testFindStringLiteralEndWithEscapedQuote() {
        String literal = "\"abc\\\"def\"";
        int end = TextParsingUtils.findStringLiteralEnd(literal, 1);
        assertEquals(literal.length() - 1, end);
    }

    public void testFindStringLiteralEndWhenUnclosed() {
        int end = TextParsingUtils.findStringLiteralEnd("\"abc", 1);
        assertEquals(-1, end);
    }

    public void testFindMatchingParenthesisNestedAndIgnoringStringContent() {
        String source = "call(a, nested(\"(\", b), c)";
        int open = source.indexOf('(');
        int end = TextParsingUtils.findMatchingParenthesis(source, open);
        assertEquals(source.length() - 1, end);
    }

    public void testFindMatchingParenthesisWhenUnclosed() {
        int end = TextParsingUtils.findMatchingParenthesis("(a(b)", 0);
        assertEquals(-1, end);
    }

    public void testSplitTopLevelArgumentsWithNestedStructures() {
        String args = "a, func(b, c), Map<String, Integer>, \"x,y\", new int[]{1,2}";
        List<String> result = TextParsingUtils.splitTopLevelArguments(args);

        assertEquals(5, result.size());
        assertEquals("a", result.get(0));
        assertEquals("func(b, c)", result.get(1));
        assertEquals("Map<String, Integer>", result.get(2));
        assertEquals("\"x,y\"", result.get(3));
        assertEquals("new int[]{1,2}", result.get(4));
    }
}
