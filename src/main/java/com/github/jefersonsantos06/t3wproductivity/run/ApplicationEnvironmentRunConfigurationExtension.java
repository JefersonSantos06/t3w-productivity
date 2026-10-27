package com.github.jefersonsantos06.t3wproductivity.run;

import com.github.jefersonsantos06.t3wproductivity.services.project.EnvironmentProjectService;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import org.jetbrains.annotations.NotNull;

public final class ApplicationEnvironmentRunConfigurationExtension extends RunConfigurationExtension {

    @Override
    public  <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull final T configuration,
                                                                            @NotNull final JavaParameters params,
                                                                            final RunnerSettings runnerSettings) {
        final var projectService = configuration.getProject().getService(EnvironmentProjectService.class);
        params.getEnv().putAll(projectService.getEffectiveEnvironment(false));
    }

    @Override
    public boolean isApplicableFor(@NotNull final RunConfigurationBase<?> configuration) {
        return configuration instanceof ApplicationConfiguration;
    }
}
