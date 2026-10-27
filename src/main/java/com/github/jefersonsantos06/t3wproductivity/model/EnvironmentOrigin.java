package com.github.jefersonsantos06.t3wproductivity.model;

/**
 * Camada de onde vem o valor efetivo de uma variável. Prioridade: PROJECT > PRESET > GLOBAL
 * (a ordem de declaração é a ordem de aplicação: cada camada sobrescreve as anteriores).
 */
public enum EnvironmentOrigin {
    GLOBAL("Global"),
    PRESET("Preset"),
    PROJECT("Projeto");

    private final String label;

    EnvironmentOrigin(final String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
