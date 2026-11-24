package com.github.jefersonsantos06.t3wproductivity.actions.refactor.utils;

import com.github.jefersonsantos06.t3wproductivity.util.WorktreeIsolationUtils;
import junit.framework.TestCase;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;

import static com.github.jefersonsantos06.t3wproductivity.util.WorktreeIsolationUtils.APP_NAME_PREFIX_KEY;
import static com.github.jefersonsantos06.t3wproductivity.util.WorktreeIsolationUtils.COOKIE_NAME_KEY;
import static com.github.jefersonsantos06.t3wproductivity.util.WorktreeIsolationUtils.SERVER_PORT_KEY;

public class WorktreeIsolationUtilsTest extends TestCase {

    public void testParsesPlainAndPlaceholderPorts() {
        assertEquals(OptionalInt.of(9000), WorktreeIsolationUtils.parsePort("9000"));
        assertEquals(OptionalInt.of(9013), WorktreeIsolationUtils.parsePort("${PORT:9013}"));
        assertEquals(OptionalInt.empty(), WorktreeIsolationUtils.parsePort("${PORT}"));
        assertEquals(OptionalInt.empty(), WorktreeIsolationUtils.parsePort(null));
    }

    public void testShiftsPortAndCookieForWorktreeSlot() {
        final var variables = WorktreeIsolationUtils.isolationVariables(
                properties("server.port", "9000", "server.servlet.session.cookie.name", "T3W_ADMIN_SESSIONID"),
                2, "jbear-rev-kdm_gdc", List.of());

        assertEquals(Map.of(
                SERVER_PORT_KEY, "9200",
                COOKIE_NAME_KEY, "T3W_ADMIN_SESSIONID_2",
                APP_NAME_PREFIX_KEY, "jbear-rev-kdm_gdc-"
        ), variables);
    }

    public void testMainCheckoutKeepsOriginalValues() {
        assertEquals(Map.of(), WorktreeIsolationUtils.isolationVariables(properties("server.port", "9000"), 0, "jbear-rev", List.of()));
    }

    public void testExplicitValuesWin() {
        final var variables = WorktreeIsolationUtils.isolationVariables(properties("server.port", "9000"), 1, "wt",
                List.of(SERVER_PORT_KEY, APP_NAME_PREFIX_KEY));

        assertEquals(Map.of(COOKIE_NAME_KEY, "JSESSIONID_1"), variables);
    }

    public void testWithoutApplicationPropertiesOnlyTagsConnections() {
        assertEquals(Map.of(APP_NAME_PREFIX_KEY, "global_search-"),
                WorktreeIsolationUtils.isolationVariables(new Properties(), 1, "global search", List.of()));
    }

    public void testAssignsLowestFreeSlotAndKeepsExisting() {
        final var slots = new LinkedHashMap<String, Integer>(Map.of("/a", 1, "/c", 3));

        assertEquals(2, WorktreeIsolationUtils.assignSlot(slots, "/b"));
        assertEquals(3, WorktreeIsolationUtils.assignSlot(slots, "/c"));
        assertEquals(4, WorktreeIsolationUtils.assignSlot(slots, "/d"));
    }

    public void testDetectsLinkedWorktreeByGitFile() throws Exception {
        final var main = Files.createTempDirectory("main");
        final var linked = Files.createTempDirectory("linked");
        Files.createDirectory(main.resolve(".git"));
        Files.writeString(linked.resolve(".git"), "gitdir: /tmp/main/.git/worktrees/linked\n");

        assertFalse(WorktreeIsolationUtils.detectWorktree(main).linked());
        assertTrue(WorktreeIsolationUtils.detectWorktree(linked).linked());
        assertEquals(linked.getFileName().toString(), WorktreeIsolationUtils.detectWorktree(linked).name());
    }

    private static Properties properties(final String... keyValues) {
        final var properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }
}
