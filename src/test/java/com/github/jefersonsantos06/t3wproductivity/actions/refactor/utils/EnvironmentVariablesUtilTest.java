package com.github.jefersonsantos06.t3wproductivity.actions.refactor.utils;

import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import junit.framework.TestCase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentVariablesUtilTest extends TestCase {

    public void testMergeEnvironmentLayersInOrder() {
        final var global = Map.of("DB", "global_db", "HOST", "localhost");
        final var preset = Map.of("DB", "preset_db", "PREFIX", "preset");
        final var project = Map.of("PREFIX", "project");

        final var merged = EnvironmentVariablesUtil.mergeEnvironment(true, global, preset, project);

        assertEquals("preset_db", merged.get("DB"));
        assertEquals("localhost", merged.get("HOST"));
        assertEquals("project", merged.get("PREFIX"));
        assertEquals(3, merged.size());
    }

    public void testMergeEnvironmentWithoutEmptyValuesKeepsLowerLayerValue() {
        final var global = Map.of("DB", "global_db");
        final var project = Map.of("DB", "");

        assertEquals("global_db", EnvironmentVariablesUtil.mergeEnvironment(false, global, project).get("DB"));
        assertEquals("", EnvironmentVariablesUtil.mergeEnvironment(true, global, project).get("DB"));
    }

    public void testMergeEnvironmentTrimsKeysAndIgnoresBlankKeys() {
        final var layer = new LinkedHashMap<String, String>();
        layer.put("  DB  ", "x");
        layer.put("  ", "ignored");

        assertEquals(Map.of("DB", "x"), EnvironmentVariablesUtil.mergeEnvironment(true, layer));
    }

    public void testSanitizeVisibleKeysRemovesDuplicatesAndBlanks() {
        assertEquals(List.of("A", "B"), EnvironmentVariablesUtil.sanitizeVisibleKeys(List.of(" A", "B", "A ", " ")));
    }
}
