package com.github.jefersonsantos06.t3wproductivity.services.project;

import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentOrigin;
import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentPreset;
import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.services.state.EnvironmentState;
import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil.STORE_NAME_PREFX;

@Service(Service.Level.PROJECT)
@State(name = STORE_NAME_PREFX + "ProjectEnvironmentService", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class EnvironmentProjectService implements PersistentStateComponent<EnvironmentState.Project> {

    private EnvironmentState.Project state = new EnvironmentState.Project();

    @Override
    public @NotNull EnvironmentState.Project getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull final EnvironmentState.Project state) {
        this.state = state;
        getState().setProjectEnvironment(EnvironmentVariablesUtil.sanitizeEnvironmentMap(getState().getProjectEnvironment()));
        getState().setActivePresetId(StringUtils.trimToNull(getState().getActivePresetId()));
        migrateVisibleKeysToGlobal();
    }

    /**
     * Variáveis visíveis eram salvas por projeto (workspace.xml), o que deixava cada worktree nova vazia.
     * Agora são globais: a primeira lista legada encontrada vira a lista global.
     */
    private void migrateVisibleKeysToGlobal() {
        final var legacyKeys = EnvironmentVariablesUtil.sanitizeVisibleKeys(getState().getVisibleEnvironmentKeys());
        if (legacyKeys.isEmpty()) {
            return;
        }
        final var appService = getApplicationService();
        if (appService.getVisibleEnvironmentKeys().isEmpty()) {
            appService.setVisibleEnvironmentKeys(legacyKeys);
        }
        getState().setVisibleEnvironmentKeys(new ArrayList<>());
    }

    public Map<String, String> getProjectEnvironment() {
        return EnvironmentVariablesUtil.sanitizeEnvironmentMap(getState().getProjectEnvironment());
    }

    public void setProjectEnvironment(final Map<String, String> projectEnvironment) {
        getState().setProjectEnvironment(EnvironmentVariablesUtil.sanitizeEnvironmentMap(projectEnvironment));
    }

    public void setProjectOverride(final String key, final String value) {
        final var projectEnv = getProjectEnvironment();
        projectEnv.put(key, value);
        setProjectEnvironment(projectEnv);
    }

    public void removeProjectOverride(final String key) {
        final var projectEnv = getProjectEnvironment();
        projectEnv.remove(key);
        setProjectEnvironment(projectEnv);
    }

    public String getActivePresetId() {
        return StringUtils.trimToNull(getState().getActivePresetId());
    }

    public void setActivePresetId(final String presetId) {
        getState().setActivePresetId(StringUtils.trimToNull(presetId));
    }

    public Optional<EnvironmentPreset> getActivePreset() {
        return getApplicationService().findPreset(getActivePresetId());
    }

    /**
     * Ambiente efetivo do projeto: Global → Preset ativo → Override do projeto.
     */
    public Map<String, String> getEffectiveEnvironment(final boolean keepEmptyValues) {
        return EnvironmentVariablesUtil.mergeEnvironment(
                keepEmptyValues,
                getApplicationService().getGlobalEnvironment(),
                getActivePresetVariables(),
                getProjectEnvironment()
        );
    }

    public Map<String, EnvironmentOrigin> getEnvironmentOrigins() {
        final var origins = new LinkedHashMap<String, EnvironmentOrigin>();
        getApplicationService().getGlobalEnvironment().keySet().forEach(key -> origins.put(key, EnvironmentOrigin.GLOBAL));
        getActivePresetVariables().keySet().forEach(key -> origins.put(key, EnvironmentOrigin.PRESET));
        getProjectEnvironment().keySet().forEach(key -> origins.put(key, EnvironmentOrigin.PROJECT));
        return origins;
    }

    /**
     * Todas as chaves conhecidas: global, todos os presets, overrides do projeto e as já marcadas como visíveis.
     */
    public Set<String> getAvailableEnvironmentKeys() {
        final var appService = getApplicationService();
        final var available = new LinkedHashSet<>(appService.getGlobalEnvironment().keySet());
        appService.getPresets().forEach(preset -> available.addAll(preset.getVariables().keySet()));
        available.addAll(getProjectEnvironment().keySet());
        available.addAll(appService.getVisibleEnvironmentKeys());
        return available;
    }

    private Map<String, String> getActivePresetVariables() {
        return getActivePreset().map(EnvironmentPreset::getVariables).orElseGet(Map::of);
    }

    private static EnvironmentApplicationService getApplicationService() {
        return ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
    }
}
