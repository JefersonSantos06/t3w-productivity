package com.github.jefersonsantos06.t3wproductivity.run;

import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.services.project.EnvironmentProjectService;
import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public final class ApplicationEnvironmentRunConfigurationExtension extends RunConfigurationExtension {

    @Override
    public  <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull final T configuration,
                                                                            @NotNull final JavaParameters params,
                                                                            final RunnerSettings runnerSettings) {
        final var project = configuration.getProject();
        final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
        final var projectService = project.getService(EnvironmentProjectService.class);

        final var mergedEnvironment = EnvironmentVariablesUtil.mergeEnvironment(
                appService.getGlobalEnvironment(), projectService.getProjectEnvironment(), false
        );
        params.getEnv().putAll(mergedEnvironment);
    }

    @Override
    public boolean isApplicableFor(@NotNull final RunConfigurationBase<?> configuration) {
        return configuration instanceof ApplicationConfiguration;
    }
}
