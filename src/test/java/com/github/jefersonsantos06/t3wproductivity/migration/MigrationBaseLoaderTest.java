package com.github.jefersonsantos06.t3wproductivity.migration;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Usa o git instalado na máquina com um repositório temporário.
 */
public class MigrationBaseLoaderTest extends TestCase {

    private static final String MIGRATIONS = "app/src/main/java/db/migration";

    private Path repository;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repository = Files.createTempDirectory("t3w-migrations");
        git("init", "-q");
        git("config", "user.email", "teste@t3w.io");
        git("config", "user.name", "Teste");
        write(MIGRATIONS + "/V202611200900__create_cliente.sql", "create table cliente (id int);\n");
        write(MIGRATIONS + "/V202611221000__funcoes.sql",
                "CREATE OR REPLACE FUNCTION public.fn_legada() RETURNS int AS $$ select 1 $$ LANGUAGE sql;\n");
        write("README.md", "fora da pasta de migrations\n");
        git("add", ".");
        git("commit", "-q", "-m", "base");
        git("update-ref", "refs/remotes/origin/master", "HEAD");
        // Trabalho local da branch: não deve aparecer na base.
        write(MIGRATIONS + "/V202611241200__nova.sql", "select 1;\n");
        git("add", ".");
        git("commit", "-q", "-m", "branch");
    }

    @Override
    protected void tearDown() throws Exception {
        try (final var paths = Files.walk(repository)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } finally {
            super.tearDown();
        }
    }

    public void testReadsMigrationsAndFunctionsFromBaseBranch() {
        final var snapshot = MigrationBaseLoader.load(repository, MIGRATIONS).orElseThrow();

        assertEquals("origin/master", snapshot.baseRef());
        assertEquals(Set.of("V202611200900__create_cliente.sql", "V202611221000__funcoes.sql"), snapshot.fileNames());
        assertEquals(Set.of("fn_legada"), snapshot.functionNames());
        assertEquals(Optional.of("create table cliente (id int);\n"),
                MigrationBaseLoader.fileContent(repository, snapshot.commit(), MIGRATIONS + "/V202611200900__create_cliente.sql"));
    }

    public void testFindsRepositoryRootFromMigrationsDirectory() {
        assertEquals(Optional.of(repository.toAbsolutePath()),
                MigrationBaseLoader.findRepositoryRoot(repository.resolve(MIGRATIONS)));
    }

    public void testHasNoSnapshotWithoutBaseBranch() throws Exception {
        git("update-ref", "-d", "refs/remotes/origin/master");

        assertEquals(Optional.empty(), MigrationBaseLoader.load(repository, MIGRATIONS));
    }

    private void write(final String relativePath, final String content) throws IOException {
        final var file = repository.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void git(final String... arguments) throws Exception {
        final var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(arguments));
        final var process = new ProcessBuilder(command).directory(repository.toFile()).inheritIO().start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals("git " + String.join(" ", arguments), 0, process.exitValue());
    }
}
