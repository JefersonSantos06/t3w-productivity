package com.github.jefersonsantos06.t3wproductivity.database;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reconhece, linha a linha, o resultado do Flyway no log de uma execução: em qual banco rodou, o schema e a versão final.
 */
public final class FlywayLogParser {

    public record Result(JdbcLocation location, String schema, String version, int appliedMigrations) {
    }

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[;\\d]*m");
    private static final Pattern DATABASE = Pattern.compile("Database: (jdbc:\\S+)");
    private static final Pattern CURRENT_VERSION = Pattern.compile("Current version of schema \"?([^\":]+)\"?: (\\S+)");
    private static final Pattern APPLIED =
            Pattern.compile("Successfully applied (\\d+) migrations? to schema \"?([^\",]+)\"?(?:, now at version v(\\S+))?");
    private static final Pattern UP_TO_DATE = Pattern.compile("Schema \"?([^\"]+)\"? is up to date\\. No migration necessary");

    private JdbcLocation location;
    private String currentVersion;

    public Optional<Result> accept(final String line) {
        if (!mayBeFlyway(line)) {
            return Optional.empty();
        }
        final var text = ANSI_ESCAPE.matcher(line).replaceAll("");

        final var database = DATABASE.matcher(text);
        if (database.find()) {
            location = JdbcLocation.parse(database.group(1)).orElse(null);
            currentVersion = null;
            return Optional.empty();
        }
        final var current = CURRENT_VERSION.matcher(text);
        if (current.find()) {
            currentVersion = current.group(2);
            return Optional.empty();
        }
        if (location == null) {
            return Optional.empty();
        }
        final var applied = APPLIED.matcher(text);
        if (applied.find()) {
            final var version = applied.group(3) == null ? currentVersion : applied.group(3);
            return Optional.of(new Result(location, applied.group(2), version, Integer.parseInt(applied.group(1))));
        }
        final var upToDate = UP_TO_DATE.matcher(text);
        if (upToDate.find()) {
            return Optional.of(new Result(location, upToDate.group(1), currentVersion, 0));
        }
        return Optional.empty();
    }

    // Filtro barato: a maior parte do log de uma aplicação não tem nada do Flyway.
    private static boolean mayBeFlyway(final String line) {
        return line.contains("Database: jdbc:") || line.contains("Current version of schema")
                || line.contains("Successfully applied") || line.contains("No migration necessary");
    }
}
