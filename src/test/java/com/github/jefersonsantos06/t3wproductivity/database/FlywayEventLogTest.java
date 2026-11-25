package com.github.jefersonsantos06.t3wproductivity.database;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class FlywayEventLogTest extends TestCase {

    private static final JdbcLocation LOCAL = new JdbcLocation("localhost", 5432, "wmx");

    private Path file;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        file = Files.createTempDirectory("t3w-flyway-events").resolve("sub").resolve("flyway-events.log");
    }

    public void testReadsEventsWrittenAfterTheReaderStarted() throws IOException {
        final var applied = new FlywayLogParser.Result(LOCAL, "public", "20260101120000", 2);
        final var upToDate = new FlywayLogParser.Result(LOCAL, "public", null, 0);
        FlywayEventLog.append(file, applied);
        final var reader = new FlywayEventLog(file);

        FlywayEventLog.append(file, applied);
        FlywayEventLog.append(file, upToDate);

        assertEquals(List.of(applied, upToDate), reader.readNew());
        assertEquals(List.of(), reader.readNew());
    }

    public void testWaitsForTheLineToBeComplete() throws IOException {
        final var reader = new FlywayEventLog(file);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "localhost\t5432\twmx\tpublic\t1\t1", StandardCharsets.UTF_8);

        assertEquals(List.of(), reader.readNew());

        Files.writeString(file, "\nlixo\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(List.of(new FlywayLogParser.Result(LOCAL, "public", "1", 1)), reader.readNew());
    }

    public void testStartsOverWhenTheFileIsRotated() throws IOException {
        final var event = new FlywayLogParser.Result(LOCAL, "public", "2", 1);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x".repeat(70 * 1024) + "\n", StandardCharsets.UTF_8);
        final var reader = new FlywayEventLog(file);

        FlywayEventLog.append(file, event);

        assertTrue(Files.size(file) < 1024);
        assertEquals(List.of(event), reader.readNew());
    }
}
