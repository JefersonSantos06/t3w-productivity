package com.github.jefersonsantos06.t3wproductivity.database;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Servidor e banco de uma URL JDBC do PostgreSQL.
 */
public record JdbcLocation(String host, int port, String database) {

    private static final Pattern POSTGRES_URL =
            Pattern.compile("^jdbc:postgresql://(\\[[^\\]]+\\]|[^/:?\\s]+)(?::(\\d+))?(?:/([^?;\\s]*))?");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");
    private static final int DEFAULT_PORT = 5432;

    public static Optional<JdbcLocation> parse(final String url) {
        if (url == null) {
            return Optional.empty();
        }
        final var matcher = POSTGRES_URL.matcher(url.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }
        final var port = matcher.group(2) == null ? DEFAULT_PORT : Integer.parseInt(matcher.group(2));
        final var database = matcher.group(3) == null ? "" : matcher.group(3);
        return Optional.of(new JdbcLocation(matcher.group(1).toLowerCase(Locale.ROOT), port, database));
    }

    public boolean isLocal() {
        return LOCAL_HOSTS.contains(host);
    }

    /**
     * Mesmo servidor local (localhost, 127.0.0.1... são equivalentes), ignorando o banco.
     */
    public boolean isSameLocalServer(final JdbcLocation other) {
        return isLocal() && other.isLocal() && port == other.port;
    }
}
