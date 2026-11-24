package com.github.jefersonsantos06.t3wproductivity.util;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Isola apps Spring rodando ao mesmo tempo em worktrees diferentes: cada worktree tem um slot e o app sobe com
 * porta e cookie de sessão próprios. O checkout principal (slot 0) não muda nada.
 */
public final class WorktreeIsolationUtils {

    public static final int PORT_STEP = 100;
    public static final String SERVER_PORT_KEY = "SERVER_PORT";
    public static final String COOKIE_NAME_KEY = "SERVER_SERVLET_SESSION_COOKIE_NAME";
    public static final String APP_NAME_PREFIX_KEY = "T3W_DEV_DB_APPNAME_PREFIX";

    private static final String DEFAULT_COOKIE_NAME = "JSESSIONID";
    private static final int MAX_PORT = 65535;
    // "9000" ou "${PORT:9013}"
    private static final Pattern PORT_VALUE = Pattern.compile("^\\s*(?:\\$\\{[^:}]+:(\\d+)}|(\\d+))\\s*$");

    private WorktreeIsolationUtils() {
    }

    public record Worktree(String path, String name, boolean linked) {
    }

    /**
     * Numa worktree vinculada o ".git" é um arquivo; no checkout principal é uma pasta.
     */
    public static Worktree detectWorktree(final Path projectRoot) {
        final var fileName = projectRoot.getFileName();
        final var name = fileName == null ? projectRoot.toString() : fileName.toString();
        return new Worktree(projectRoot.toString(), name, Files.isRegularFile(projectRoot.resolve(".git")));
    }

    /**
     * Slot já atribuído à pasta ou o menor livre a partir de 1 (gravado em {@code slots}).
     */
    public static int assignSlot(final Map<String, Integer> slots, final String worktreePath) {
        final var existing = slots.get(worktreePath);
        if (existing != null && existing > 0) {
            return existing;
        }
        final Collection<Integer> used = slots.values();
        var slot = 1;
        while (used.contains(slot)) {
            slot++;
        }
        slots.put(worktreePath, slot);
        return slot;
    }

    public static OptionalInt parsePort(@Nullable final String value) {
        if (value == null) {
            return OptionalInt.empty();
        }
        final var matcher = PORT_VALUE.matcher(value);
        if (!matcher.matches()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2)));
    }

    /**
     * Variáveis a injetar na execução. Chaves já definidas (preset, override, run configuration) nunca são trocadas.
     *
     * @param applicationProperties o application.properties do módulo; vazio quando não encontrado
     */
    public static Map<String, String> isolationVariables(final Properties applicationProperties, final int slot,
                                                         final String worktreeName, final Collection<String> definedKeys) {
        final var variables = new LinkedHashMap<String, String>();
        if (slot <= 0) {
            return variables;
        }
        if (!applicationProperties.isEmpty()) {
            parsePort(applicationProperties.getProperty("server.port"))
                    .stream()
                    .map(port -> port + PORT_STEP * slot)
                    .filter(port -> port <= MAX_PORT)
                    .findFirst()
                    .ifPresent(port -> variables.put(SERVER_PORT_KEY, String.valueOf(port)));
            final var cookieName = applicationProperties.getProperty("server.servlet.session.cookie.name", DEFAULT_COOKIE_NAME).strip();
            variables.put(COOKIE_NAME_KEY, cookieName + "_" + slot);
        }
        variables.put(APP_NAME_PREFIX_KEY, worktreeName.replaceAll("[^A-Za-z0-9_-]", "_") + "-");
        definedKeys.forEach(variables::remove);
        return variables;
    }
}
