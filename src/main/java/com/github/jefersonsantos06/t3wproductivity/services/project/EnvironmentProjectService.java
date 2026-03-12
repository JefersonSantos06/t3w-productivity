package com.github.jefersonsantos06.t3wproductivity.services.project;

import com.github.jefersonsantos06.t3wproductivity.services.state.EnvironmentState;
import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        getState().setVisibleEnvironmentKeys(EnvironmentVariablesUtil.sanitizeVisibleKeys(getState().getVisibleEnvironmentKeys()));
    }

    public Map<String, String> getProjectEnvironment() {
        return EnvironmentVariablesUtil.sanitizeEnvironmentMap(getState().getProjectEnvironment());
    }

    public void setProjectEnvironment(final Map<String, String> projectEnvironment) {
        getState().setProjectEnvironment(EnvironmentVariablesUtil.sanitizeEnvironmentMap(projectEnvironment));
    }

    public List<String> getVisibleEnvironmentKeys() {
        return List.copyOf(getState().getVisibleEnvironmentKeys());
    }

    public void setVisibleEnvironmentKeys(final List<String> visibleEnvironmentKeys, final Set<String> availableKeys) {
        getState().setVisibleEnvironmentKeys(EnvironmentVariablesUtil.sanitizeVisibleKeys(visibleEnvironmentKeys, availableKeys));
    }

    public void syncVisibleEnvironmentKeys(final Set<String> availableKeys) {
        getState().setVisibleEnvironmentKeys(EnvironmentVariablesUtil.sanitizeVisibleKeys(getState().getVisibleEnvironmentKeys(), availableKeys));
    }

    public Set<String> getAvailableEnvironmentKeys(final Map<String, String> globalEnvironment) {
        final var available = new LinkedHashSet<String>();
        available.addAll(EnvironmentVariablesUtil.sanitizeEnvironmentMap(globalEnvironment).keySet());
        available.addAll(getProjectEnvironment().keySet());
        return available;
    }
}
