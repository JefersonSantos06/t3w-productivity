package com.github.jefersonsantos06.t3wproductivity.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lê do git as migrations da branch base. Usa a base como está no repositório local (não faz fetch).
 */
public final class MigrationBaseLoader {

    private static final long TIMEOUT_SECONDS = 15;
    private static final List<String> FALLBACK_REFS = List.of("origin/master", "origin/main");

    private MigrationBaseLoader() {
    }

    /**
     * Primeira pasta acima de {@code start} com ".git" (diretório no checkout principal, arquivo numa worktree).
     */
    public static Optional<Path> findRepositoryRoot(final Path start) {
        for (var current = start.toAbsolutePath(); current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"))) {
                return Optional.of(current);
            }
        }
        return Optional.empty();
    }

    /**
     * Branch padrão do remoto (origin/HEAD) ou, sem ela, origin/master ou origin/main.
     */
    public static Optional<String> resolveBaseRef(final Path repositoryRoot) {
        final var originHead = git(repositoryRoot, "symbolic-ref", "--quiet", "--short", "refs/remotes/origin/HEAD")
                .map(String::strip)
                .filter(ref -> !ref.isEmpty());
        if (originHead.isPresent()) {
            return originHead;
        }
        return FALLBACK_REFS.stream()
                .filter(ref -> git(repositoryRoot, "rev-parse", "--verify", "--quiet", ref).isPresent())
                .findFirst();
    }

    /**
     * @param relativeDirectory pasta das migrations relativa à raiz do repositório, com "/"
     */
    public static Optional<MigrationBaseSnapshot> load(final Path repositoryRoot, final String relativeDirectory) {
        final var baseRef = resolveBaseRef(repositoryRoot).orElse(null);
        if (baseRef == null) {
            return Optional.empty();
        }
        final var commit = git(repositoryRoot, "rev-parse", baseRef).map(String::strip).orElse(null);
        if (commit == null) {
            return Optional.empty();
        }
        final var fileNames = new LinkedHashSet<String>();
        git(repositoryRoot, "ls-tree", "-r", "--name-only", commit, "--", relativeDirectory)
                .ifPresent(output -> output.lines()
                        .filter(line -> !line.isBlank())
                        .forEach(path -> fileNames.add(path.substring(path.lastIndexOf('/') + 1))));
        // Sem ocorrências o git grep sai com código 1: tratado como "nenhuma função".
        final var functionNames = git(repositoryRoot, "grep", "-h", "-i", "-o", "-E",
                "create[[:space:]]+(or[[:space:]]+replace[[:space:]]+)?function[[:space:]]+[^([:space:]]+",
                commit, "--", relativeDirectory)
                .map(MigrationGuardRules::createdFunctions)
                .orElseGet(LinkedHashSet::new);
        return Optional.of(new MigrationBaseSnapshot(baseRef, commit, fileNames, functionNames));
    }

    /**
     * Conteúdo de um arquivo num commit; {@code relativePath} relativo à raiz do repositório, com "/".
     */
    public static Optional<String> fileContent(final Path repositoryRoot, final String commit, final String relativePath) {
        return git(repositoryRoot, "show", commit + ":" + relativePath);
    }

    private static Optional<String> git(final Path workingDirectory, final String... arguments) {
        final var command = new ArrayList<String>();
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        try {
            final var process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            final var output = CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (final IOException e) {
                    return "";
                }
            });
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            return process.exitValue() == 0 ? Optional.of(output.join()) : Optional.empty();
        } catch (final IOException e) {
            return Optional.empty();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
