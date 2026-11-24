package com.github.jefersonsantos06.t3wproductivity.database;

import junit.framework.TestCase;

import java.util.List;
import java.util.Optional;

public class FlywayLogParserTest extends TestCase {

    private static final String PREFIX = "2026-11-24T12:00:00.123-03:00  INFO 4242 --- [  restartedMain] o.f.c.i.command.DbMigrate : ";
    private static final JdbcLocation WMX = new JdbcLocation("localhost", 5432, "wmx");

    public void testReportsAppliedMigrations() {
        final var result = parse(
                PREFIX + "Database: jdbc:postgresql://localhost:5432/wmx?ApplicationName=jbear-admin (PostgreSQL 18.6)",
                PREFIX + "Current version of schema \"public\": 202611200900",
                PREFIX + "Migrating schema \"public\" to version \"202611241200 - Add table\"",
                PREFIX + "Successfully applied 2 migrations to schema \"public\", now at version v202611241300 (execution time 00:00.120s)"
        );

        assertEquals(Optional.of(new FlywayLogParser.Result(WMX, "public", "202611241300", 2)), result);
    }

    public void testReportsSchemaUpToDateWithCurrentVersion() {
        final var result = parse(
                PREFIX + "Database: jdbc:postgresql://localhost:5432/wmx (PostgreSQL 18.6)",
                PREFIX + "Current version of schema \"public\": 202611200900",
                PREFIX + "Schema \"public\" is up to date. No migration necessary."
        );

        assertEquals(Optional.of(new FlywayLogParser.Result(WMX, "public", "202611200900", 0)), result);
    }

    public void testAcceptsSingleMigrationColorsAndWindowsLineEndings() {
        final var result = parse(
                "\u001B[32mINFO\u001B[0m Database: jdbc:postgresql://127.0.0.1/wmx (PostgreSQL 18.6)\r",
                "\u001B[32mINFO\u001B[0m Successfully applied 1 migration to schema \"public\", now at version v202611241300 (execution time 00:00.010s)\r"
        );

        assertEquals(Optional.of(new FlywayLogParser.Result(new JdbcLocation("127.0.0.1", 5432, "wmx"), "public", "202611241300", 1)),
                result);
    }

    public void testIgnoresResultWithoutDatabaseLine() {
        assertEquals(Optional.empty(), parse(PREFIX + "Schema \"public\" is up to date. No migration necessary."));
    }

    public void testIgnoresUnrelatedLines() {
        assertEquals(Optional.empty(), parse("Started AdminApplication in 12.3 seconds", "Tomcat started on port 9000"));
    }

    private static Optional<FlywayLogParser.Result> parse(final String... lines) {
        final var parser = new FlywayLogParser();
        return List.of(lines).stream()
                .map(parser::accept)
                .flatMap(Optional::stream)
                .reduce((first, second) -> second);
    }
}
