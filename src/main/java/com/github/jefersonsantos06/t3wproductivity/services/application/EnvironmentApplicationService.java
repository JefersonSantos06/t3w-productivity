package com.github.jefersonsantos06.t3wproductivity.services.application;

import com.github.jefersonsantos06.t3wproductivity.services.state.EnvironmentState;
import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

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
    }

    public Map<String, String> getGlobalEnvironment() {
        return new LinkedHashMap<>(EnvironmentVariablesUtil.sanitizeEnvironmentMap(getState().getGlobalEnvironment()));
    }

    public void setGlobalEnvironment(final Map<String, String> environment) {
        getState().setGlobalEnvironment(EnvironmentVariablesUtil.sanitizeEnvironmentMap(environment));
    }
}
