package com.github.jefersonsantos06.t3wproductivity.util;

import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentVariable;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public final class EnvironmentVariablesUtil {

    public static final String STORE_NAME_PREFX = "JSantos";

    private EnvironmentVariablesUtil() {
    }

    public static Map<String, String> sanitizeEnvironmentMap(final Map<String, String> source) {
        return sanitizeEnvironmentMap(source, true);
    }


    public static Map<String, String> sanitizeEnvironmentMap(final Map<String, String> source, final boolean keepEmptyValues) {
        final var sanitized = new LinkedHashMap<String, String>();
        if (source == null) {
            return sanitized;
        }

        source.forEach((key, value) -> {
            final var normalizedKey = StringUtils.trimToNull(key);
            if (normalizedKey == null || (!keepEmptyValues && StringUtils.isBlank(value))) {
                return;
            }
            sanitized.put(normalizedKey, Optional.ofNullable(value).orElse(""));
        });
        return sanitized;
    }

    public static List<String> sanitizeVisibleKeys(final Collection<String> keys, final Collection<String> availableKeys) {
        final var availableSet = new LinkedHashSet<>(Optional.ofNullable(availableKeys).orElseGet(List::of));
        final var sanitizedKeys = sanitizeVisibleKeys(keys);
        final var result = new ArrayList<String>();
        for (final var key : sanitizedKeys) {
            if (availableSet.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    public static List<String> sanitizeVisibleKeys(final Collection<String> keys) {
        final var result = new ArrayList<String>();
        final var unique = new LinkedHashSet<String>();

        for (final var key : Optional.ofNullable(keys).orElseGet(List::of)) {
            final var normalized = StringUtils.trimToNull(key);
            if (normalized == null) {
                continue;
            }
            if (unique.add(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    public static Map<String, String> mergeEnvironment(final Map<String, String> globalEnv, final Map<String, String> projectEnv) {
        return mergeEnvironment(globalEnv, projectEnv, true);
    }

    public static Map<String, String> mergeEnvironment(final Map<String, String> globalEnv, final Map<String, String> projectEnv, boolean keepEmptyValues) {
        final var merged = new LinkedHashMap<>(sanitizeEnvironmentMap(globalEnv, keepEmptyValues));
        merged.putAll(sanitizeEnvironmentMap(projectEnv, keepEmptyValues));
        return merged;
    }

    public static List<EnvironmentVariable> toEntries(final Map<String, String> source) {
        final var entries = new ArrayList<EnvironmentVariable>();
        sanitizeEnvironmentMap(source).forEach((key, value) -> entries.add(new EnvironmentVariable(key, value)));
        return entries;
    }

    public static Map<String, String> fromEntries(final Collection<EnvironmentVariable> entries) {
        final var map = new LinkedHashMap<String, String>();
        Optional.ofNullable(entries).orElseGet(List::of)
                .forEach(entry -> map.put(entry.getKey(), entry.getValue()));
        return sanitizeEnvironmentMap(map);
    }
}
