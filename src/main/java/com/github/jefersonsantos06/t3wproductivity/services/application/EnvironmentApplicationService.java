package com.github.jefersonsantos06.t3wproductivity.services.application;

import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentPreset;
import com.github.jefersonsantos06.t3wproductivity.services.state.EnvironmentState;
import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil.STORE_NAME_PREFX;

@Service(Service.Level.APP)
@State(name = STORE_NAME_PREFX + "GlobalEnvService", storages = @Storage(STORE_NAME_PREFX + "-productivity.xml"))
public final class EnvironmentApplicationService implements PersistentStateComponent<EnvironmentState.Global> {

    private EnvironmentState.Global state = new EnvironmentState.Global();

    @Override
    public @NotNull EnvironmentState.Global getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull final EnvironmentState.Global state) {
        this.state = state;
        getState().setGlobalEnvironment(EnvironmentVariablesUtil.sanitizeEnvironmentMap(getState().getGlobalEnvironment()));
        getState().setPresets(sanitizePresets(getState().getPresets()));
        getState().setVisibleEnvironmentKeys(EnvironmentVariablesUtil.sanitizeVisibleKeys(getState().getVisibleEnvironmentKeys()));
    }

    public Map<String, String> getGlobalEnvironment() {
        return new LinkedHashMap<>(EnvironmentVariablesUtil.sanitizeEnvironmentMap(getState().getGlobalEnvironment()));
    }

    public void setGlobalEnvironment(final Map<String, String> environment) {
        getState().setGlobalEnvironment(EnvironmentVariablesUtil.sanitizeEnvironmentMap(environment));
    }

    public List<EnvironmentPreset> getPresets() {
        return sanitizePresets(getState().getPresets());
    }

    public void setPresets(final Collection<EnvironmentPreset> presets) {
        getState().setPresets(sanitizePresets(presets));
    }

    public Optional<EnvironmentPreset> findPreset(final String presetId) {
        if (presetId == null) {
            return Optional.empty();
        }
        return getPresets().stream()
                .filter(preset -> presetId.equals(preset.getId()))
                .findFirst();
    }

    public List<String> getVisibleEnvironmentKeys() {
        return List.copyOf(getState().getVisibleEnvironmentKeys());
    }

    public void setVisibleEnvironmentKeys(final Collection<String> visibleEnvironmentKeys) {
        getState().setVisibleEnvironmentKeys(EnvironmentVariablesUtil.sanitizeVisibleKeys(visibleEnvironmentKeys));
    }

    private static List<EnvironmentPreset> sanitizePresets(final Collection<EnvironmentPreset> presets) {
        final var sanitized = new ArrayList<EnvironmentPreset>();
        Optional.ofNullable(presets).orElseGet(List::of).stream()
                .filter(Objects::nonNull)
                .forEach(preset -> sanitized.add(new EnvironmentPreset(
                        Optional.ofNullable(StringUtils.trimToNull(preset.getId())).orElseGet(() -> UUID.randomUUID().toString()),
                        Optional.ofNullable(StringUtils.trimToNull(preset.getName())).orElse("Preset"),
                        EnvironmentVariablesUtil.sanitizeEnvironmentMap(preset.getVariables())
                )));
        return sanitized;
    }
}
