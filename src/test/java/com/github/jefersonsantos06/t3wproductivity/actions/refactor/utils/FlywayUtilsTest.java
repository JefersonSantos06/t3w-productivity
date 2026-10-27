package com.github.jefersonsantos06.t3wproductivity.actions.refactor.utils;

import com.github.jefersonsantos06.t3wproductivity.util.FlywayUtils;
import junit.framework.TestCase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class FlywayUtilsTest extends TestCase {

    public void testFindLatestMigrationUsesHighestVersion() {
        final var latest = FlywayUtils.findLatestMigration(List.of(
                "V202601011200__a.sql",
                "V202612311159__c.sql",
                "V202606151030__b.sql"
        ));
        assertEquals(Optional.of("V202612311159__c.sql"), latest);
    }

    public void testFindLatestMigrationIgnoresNonMigrationFiles() {
        final var latest = FlywayUtils.findLatestMigration(List.of(
                "README.md",
                "R__view.sql",
                "V202601011200__a.sql",
                "V999999999999.sql",
                "U202701011200__undo.sql"
        ));
        assertEquals(Optional.of("V202601011200__a.sql"), latest);
    }

    public void testFindLatestMigrationConsidersJavaMigrations() {
        final var latest = FlywayUtils.findLatestMigration(List.of(
                "V202601011200__a.sql",
                "V202601011201__java_migration.java"
        ));
        assertEquals(Optional.of("V202601011201__java_migration.java"), latest);
    }

    public void testFindLatestMigrationComparesVersionPartsNumerically() {
        final var latest = FlywayUtils.findLatestMigration(List.of("V1.9__a.sql", "V1.10__b.sql", "V1_2__c.sql"));
        assertEquals(Optional.of("V1.10__b.sql"), latest);
    }

    public void testFindLatestMigrationEmpty() {
        assertEquals(Optional.empty(), FlywayUtils.findLatestMigration(List.of("notes.txt")));
    }

    public void testNextFreeVersionDateTimeKeepsMinuteWhenFree() {
        final var start = LocalDateTime.of(2026, 10, 27, 15, 30, 42);
        final var result = FlywayUtils.nextFreeVersionDateTime(start, List.of("V202610271529__a.sql"));
        assertEquals(LocalDateTime.of(2026, 10, 27, 15, 30), result);
    }

    public void testNextFreeVersionDateTimeSkipsUsedVersions() {
        final var start = LocalDateTime.of(2026, 10, 27, 15, 30, 42);
        final var result = FlywayUtils.nextFreeVersionDateTime(start, List.of(
                "V202610271530__a.sql",
                "V202610271531__b.java"
        ));
        assertEquals(LocalDateTime.of(2026, 10, 27, 15, 32), result);
    }

    public void testExtractMigrationDescription() {
        assertEquals("cria_tabela", FlywayUtils.extractMigrationDescription("V202601011200__cria_tabela"));
        assertEquals("cria_tabela", FlywayUtils.extractMigrationDescription("V1__Cria Tabela"));
        assertEquals("migration", FlywayUtils.extractMigrationDescription("___"));
    }

    public void testResolveDirectory() {
        assertEquals("/repo/db/migration", FlywayUtils.resolveDirectory("db/migration", "/repo"));
        assertEquals("/abs/db", FlywayUtils.resolveDirectory("/abs/db", "/repo"));
        assertEquals("C:/abs/db", FlywayUtils.resolveDirectory("C:\\abs\\db", "/repo"));
        assertEquals("/repo/" + FlywayUtils.FLYWAY_DEFAULT_PATH, FlywayUtils.resolveDirectory(" ", "/repo"));
    }
}
