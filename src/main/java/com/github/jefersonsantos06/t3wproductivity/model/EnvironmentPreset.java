package com.github.jefersonsantos06.t3wproductivity.model;

import java.util.*;

public final class EnvironmentPreset {

    private String id = "";
    private String name = "";
    private Map<String, String> variables = new LinkedHashMap<>();

    public EnvironmentPreset() {
    }

    public EnvironmentPreset(final String id, final String name, final Map<String, String> variables) {
        this.id = id;
        this.name = name;
        this.variables = new LinkedHashMap<>(Optional.ofNullable(variables).orElseGet(Map::of));
    }

    public static EnvironmentPreset create(final String name, final Map<String, String> variables) {
        return new EnvironmentPreset(UUID.randomUUID().toString(), name, variables);
    }

    public EnvironmentPreset copy() {
        return new EnvironmentPreset(id, name, variables);
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(final Map<String, String> variables) {
        this.variables = variables;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EnvironmentPreset preset)) {
            return false;
        }
        return Objects.equals(id, preset.id)
               && Objects.equals(name, preset.name)
               && Objects.equals(variables, preset.variables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, variables);
    }
}
