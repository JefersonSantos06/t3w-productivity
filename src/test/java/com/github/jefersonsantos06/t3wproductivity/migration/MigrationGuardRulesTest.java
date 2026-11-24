package com.github.jefersonsantos06.t3wproductivity.migration;

import com.github.jefersonsantos06.t3wproductivity.migration.MigrationGuardRules.Kind;
import junit.framework.TestCase;

import java.util.List;
import java.util.Set;

public class MigrationGuardRulesTest extends TestCase {

    private static final MigrationBaseSnapshot BASE = new MigrationBaseSnapshot("origin/master", "abc123",
            Set.of("V202611200900__create_cliente.sql", "V202611221000__add_email.sql"),
            Set.of("fn_legada"));

    public void testWarnsWhenMergedMigrationIsEdited() {
        final var warnings = MigrationGuardRules.evaluate("V202611200900__create_cliente.sql", "create table cliente (id int, nome text);",
                "create table cliente (id int);\n", BASE, List.of());

        assertEquals(List.of(Kind.MERGED_EDITED), kinds(warnings));
        assertTrue(warnings.get(0).message().contains("origin/master"));
    }

    public void testIgnoresMergedMigrationWithOnlyLineEndingChanges() {
        final var warnings = MigrationGuardRules.evaluate("V202611200900__create_cliente.sql", "create table cliente (id int);\r\n",
                "create table cliente (id int);\n", BASE, List.of());

        assertEquals(List.of(), warnings);
    }

    public void testWarnsWhenNewMigrationIsOlderThanLatestInBase() {
        final var warnings = MigrationGuardRules.evaluate("V202611210800__add_tabela.sql", "create table t3w_x (id int);", null,
                BASE, List.of("V202611210800__add_tabela.sql"));

        assertEquals(List.of(Kind.OUT_OF_ORDER), kinds(warnings));
        assertTrue(warnings.get(0).message().contains("V202611221000__add_email.sql"));
    }

    public void testWarnsOnDuplicatedVersion() {
        final var warnings = MigrationGuardRules.evaluate("V202611221000__outra.sql", "select 1;", null, BASE,
                List.of("V202611221000__outra.sql"));

        assertEquals(List.of(Kind.DUPLICATE_VERSION), kinds(warnings));
    }

    public void testWarnsOnlyForNewFunctionsWithoutPrefix() {
        final var sql = """
                -- create function comentario_ignorado()
                CREATE OR REPLACE FUNCTION public.fn_legada() RETURNS int AS $$ select 1 $$ LANGUAGE sql;
                create function public."calcula_total"(valor numeric) returns numeric as $$ select valor $$ language sql;
                create function t3w_search_normalize(texto text) returns text as $$ select texto $$ language sql;
                """;

        final var warnings = MigrationGuardRules.evaluate("V202611241200__funcoes.sql", sql, null, BASE, List.of());

        assertEquals(List.of(Kind.FUNCTION_WITHOUT_PREFIX), kinds(warnings));
        assertTrue(warnings.get(0).message().endsWith("calcula_total."));
    }

    public void testNewMigrationAfterLatestHasNoWarnings() {
        final var warnings = MigrationGuardRules.evaluate("V202611241200__nova.sql", "alter table cliente add column x int;", null,
                BASE, List.of("V202611241200__nova.sql"));

        assertEquals(List.of(), warnings);
    }

    public void testExtractsCreatedFunctionNames() {
        assertEquals(Set.of("fn_a", "fn_b"), MigrationGuardRules.createdFunctions(
                "create function fn_a() ...; CREATE OR REPLACE FUNCTION \"Public\".\"FN_B\"(x int) ...; /* create function fn_c() */"));
    }

    private static List<Kind> kinds(final List<MigrationGuardRules.Warning> warnings) {
        return warnings.stream().map(MigrationGuardRules.Warning::kind).toList();
    }
}
