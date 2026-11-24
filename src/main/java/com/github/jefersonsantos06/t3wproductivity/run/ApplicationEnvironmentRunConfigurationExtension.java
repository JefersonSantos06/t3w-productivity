package com.github.jefersonsantos06.t3wproductivity.run;

import com.github.jefersonsantos06.t3wproductivity.services.project.EnvironmentProjectService;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ApplicationEnvironmentRunConfigurationExtension extends RunConfigurationExtension {

    private static final Key<WorktreeIsolationSupport.Isolation> ISOLATION = Key.create("t3w.worktree.isolation");

    @Override
    public  <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull final T configuration,
                                                                            @NotNull final JavaParameters params,
                                                                            final RunnerSettings runnerSettings) {
        final var projectService = configuration.getProject().getService(EnvironmentProjectService.class);
        final var env = params.getEnv();
        env.putAll(projectService.getEffectiveEnvironment(false));
        final var isolation = WorktreeIsolationSupport.compute(configuration, env.keySet()).orElse(null);
        if (isolation != null) {
            env.putAll(isolation.variables());
        }
        configuration.putUserData(ISOLATION, isolation);
    }

    // Mostra no console da execução a porta e o cookie usados pela worktree.
    @Override
    protected void attachToProcess(@NotNull final RunConfigurationBase<?> configuration, @NotNull final ProcessHandler handler,
                                   @Nullable final RunnerSettings runnerSettings) {
        final var isolation = configuration.getUserData(ISOLATION);
        configuration.putUserData(ISOLATION, null);
        if (isolation == null) {
            return;
        }
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void startNotified(@NotNull final ProcessEvent event) {
                handler.notifyTextAvailable(isolation.describe(), ProcessOutputTypes.SYSTEM);
            }
        });
    }

    @Override
    public boolean isApplicableFor(@NotNull final RunConfigurationBase<?> configuration) {
        return configuration instanceof ApplicationConfiguration;
    }
}
