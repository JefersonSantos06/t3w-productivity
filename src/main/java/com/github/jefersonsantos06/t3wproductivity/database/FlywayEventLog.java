package com.github.jefersonsantos06.t3wproductivity.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Ponte entre IDEs: a que roda o app (IntelliJ) grava o resultado do Flyway e as que têm o plugin Database (ela mesma,
 * DataGrip...) leem e atualizam os seus data sources. Uma linha por evento, separada por tab.
 */
public final class FlywayEventLog {

    public static final Path SHARED = Path.of(System.getProperty("user.home"), ".t3w-productivity", "flyway-events.log");
    private static final long MAX_BYTES = 64 * 1024;

    private final Path path;
    private long offset;

    // Começa do fim: eventos gravados antes de a IDE abrir já não interessam.
    public FlywayEventLog(final Path path) {
        this.path = path;
        this.offset = size(path);
    }

    public static void append(final Path path, final FlywayLogParser.Result result) throws IOException {
        final var location = result.location();
        final var line = String.join("\t", location.host(), String.valueOf(location.port()), location.database(),
                result.schema(), Objects.toString(result.version(), ""), String.valueOf(result.appliedMigrations())) + "\n";
        Files.createDirectories(path.getParent());
        // Recomeça o arquivo quando cresce demais; os leitores voltam ao início ao ver o tamanho encolher.
        final var mode = size(path) > MAX_BYTES ? TRUNCATE_EXISTING : APPEND;
        Files.writeString(path, line, StandardCharsets.UTF_8, CREATE, WRITE, mode);
    }

    /**
     * Eventos gravados desde a última leitura; uma linha ainda incompleta fica para a próxima.
     */
    public synchronized List<FlywayLogParser.Result> readNew() throws IOException {
        final var size = size(path);
        if (size == offset) {
            return List.of();
        }
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (final NoSuchFileException e) {
            offset = 0;
            return List.of();
        }
        if (bytes.length < offset) {
            offset = 0;
        }
        var end = bytes.length - 1;
        while (end >= offset && bytes[end] != '\n') {
            end--;
        }
        if (end < offset) {
            return List.of();
        }
        final var chunk = new String(bytes, (int) offset, (int) (end + 1 - offset), StandardCharsets.UTF_8);
        offset = end + 1;

        final var results = new ArrayList<FlywayLogParser.Result>();
        for (final var line : chunk.split("\n")) {
            parse(line).ifPresent(results::add);
        }
        return results;
    }

    private static Optional<FlywayLogParser.Result> parse(final String line) {
        final var fields = line.split("\t", -1);
        if (fields.length != 6) {
            return Optional.empty();
        }
        try {
            final var location = new JdbcLocation(fields[0], Integer.parseInt(fields[1]), fields[2]);
            final var version = fields[4].isEmpty() ? null : fields[4];
            return Optional.of(new FlywayLogParser.Result(location, fields[3], version, Integer.parseInt(fields[5])));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static long size(final Path path) {
        try {
            return Files.size(path);
        } catch (final IOException e) {
            return 0;
        }
    }
}
