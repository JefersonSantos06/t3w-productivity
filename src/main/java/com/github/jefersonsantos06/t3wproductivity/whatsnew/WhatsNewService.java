package com.github.jefersonsantos06.t3wproductivity.whatsnew;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

import static com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil.STORE_NAME_PREFX;

/**
 * Lembra a última versão do plugin cujas novidades já foram apresentadas (global: uma vez por versão, não por projeto)
 * e guarda o descritor do plugin, recebido pela {@link WhatsNewStartupActivity}.
 */
@Service(Service.Level.APP)
@State(name = STORE_NAME_PREFX + "WhatsNew", storages = @Storage(STORE_NAME_PREFX + "-productivity.xml"))
public final class WhatsNewService implements PersistentStateComponent<WhatsNewService.State> {

    public static final class State {
        public String lastSeenVersion;
    }

    private State state = new State();
    private volatile PluginDescriptor plugin;

    public static WhatsNewService getInstance() {
        return ApplicationManager.getApplication().getService(WhatsNewService.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull final State state) {
        this.state = state;
    }

    public void setPlugin(final PluginDescriptor plugin) {
        this.plugin = plugin;
    }

    /**
     * Descritor do plugin com change notes (vazio antes da primeira abertura de projeto ou sem notas).
     */
    public Optional<PluginDescriptor> currentPlugin() {
        return Optional.ofNullable(plugin)
                .filter(descriptor -> descriptor.getChangeNotes() != null && !descriptor.getChangeNotes().isBlank());
    }

    /**
     * Registra a versão como apresentada. Devolve {@code true} só na primeira vez de cada versão.
     */
    public synchronized boolean markSeen(final String version) {
        if (Objects.equals(state.lastSeenVersion, version)) {
            return false;
        }
        state.lastSeenVersion = version;
        return true;
    }
}
