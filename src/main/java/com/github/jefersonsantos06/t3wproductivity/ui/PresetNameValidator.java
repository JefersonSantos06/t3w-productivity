package com.github.jefersonsantos06.t3wproductivity.ui;

import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentPreset;
import com.intellij.openapi.ui.InputValidatorEx;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exige nome não vazio e único entre os presets (ignorando o preset que está sendo renomeado).
 */
public final class PresetNameValidator implements InputValidatorEx {

    private final Set<String> usedNames;

    public PresetNameValidator(final Collection<EnvironmentPreset> presets, @Nullable final String ignoredPresetId) {
        this.usedNames = presets.stream()
                .filter(preset -> !Objects.equals(preset.getId(), ignoredPresetId))
                .map(preset -> normalize(preset.getName()))
                .collect(Collectors.toSet());
    }

    @Override
    public @Nullable String getErrorText(final String inputString) {
        final var name = normalize(inputString);
        if (name.isEmpty()) {
            return "Informe um nome.";
        }
        if (usedNames.contains(name)) {
            return "Já existe um preset com esse nome.";
        }
        return null;
    }

    @Override
    public boolean checkInput(final String inputString) {
        return getErrorText(inputString) == null;
    }

    @Override
    public boolean canClose(final String inputString) {
        return checkInput(inputString);
    }

    private static String normalize(final String name) {
        return StringUtils.trimToEmpty(name).toLowerCase(Locale.ROOT);
    }
}
