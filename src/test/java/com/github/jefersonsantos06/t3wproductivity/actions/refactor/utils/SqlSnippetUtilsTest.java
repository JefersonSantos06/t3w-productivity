package com.github.jefersonsantos06.t3wproductivity.actions.refactor.utils;

import com.github.jefersonsantos06.t3wproductivity.util.SqlSnippetUtils;
import junit.framework.TestCase;

import java.util.List;

public class SqlSnippetUtilsTest extends TestCase {

    public void testInferHintFromSqlTokensUsingEqualsPriority() {
        String hint = SqlSnippetUtils.inferHintFromSqlTokens(List.of("WHERE", "id_banc", "="));
        assertEquals("idBanc", hint);
    }

    public void testInferHintFromSqlTokensFallbackByLastIdentifier() {
        String hint = SqlSnippetUtils.inferHintFromSqlTokens(List.of("SELECT", "*", "id_nume"));
        assertEquals("idNume", hint);
    }

    public void testIsLowSignalSqlHint() {
        assertTrue(SqlSnippetUtils.isLowSignalSqlHint("where"));
        assertFalse(SqlSnippetUtils.isLowSignalSqlHint("idBanc"));
    }

    public void testCountSqlPlaceholdersUntilCountsOnlyInsideStringLiterals() {
        String source = "String sql = \"a ?\"; int x = ?; String t = \"b ?\";";
        int count = SqlSnippetUtils.countSqlPlaceholdersUntil(source, source.length());
        assertEquals(2, count);
    }

    public void testSplitCommaSeparated() {
        List<String> result = SqlSnippetUtils.splitCommaSeparated(" a, b ,, c ");
        assertEquals(List.of("a", "b", "c"), result);
    }

    public void testStripQualifier() {
        assertEquals("id_banc", SqlSnippetUtils.stripQualifier("mov.id_banc"));
        assertEquals("id_banc", SqlSnippetUtils.stripQualifier("id_banc"));
    }

    public void testIsSqlIdentifier() {
        assertTrue(SqlSnippetUtils.isSqlIdentifier("id_banc"));
        assertFalse(SqlSnippetUtils.isSqlIdentifier("1abc"));
    }

    public void testExtractQuotedFragments() {
        String text = "sql.append(\"SELECT * \"); sql.append(\"WHERE x=\\\\\\\"A\\\\\\\"\")";
        List<String> fragments = SqlSnippetUtils.extractQuotedFragments(text);
        assertEquals(2, fragments.size());
        assertEquals("SELECT * ", fragments.get(0));
        assertEquals("WHERE x=\\\"A\\\"", fragments.get(1));
    }

    public void testNormalizeInsertValuesLine() {
        String normalized = SqlSnippetUtils.normalizeInsertValuesLine("VALUES (:idNume,:idBanc) ", true);
        assertEquals("VALUES (idNume, idBanc) ", normalized);
        assertEquals("VALUES (:idNume,:idBanc) ", SqlSnippetUtils.normalizeInsertValuesLine("VALUES (:idNume,:idBanc) ", false));
    }
}
