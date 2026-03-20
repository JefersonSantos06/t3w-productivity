package com.github.jefersonsantos06.t3wproductivity.run;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public final class FixedMainClassRunner {

    public static final String O02_USUARIOS_CALLBACK_FQN = "wmix.vaadin.admin.flyway.O02_UsuariosCallback";

    private static final String HINT_MODULE_NAME = "jbear-vaadin-admin";

    private FixedMainClassRunner() {
    }

    public static void runO02UsuariosCallback(@NotNull final Project project) {
        if (DumbService.getInstance(project).isDumb()) {
            Messages.showWarningDialog(
                    project,
                    "Aguarde a indexacao do projeto terminar antes de executar.",
                    "T3W Productivity"
            );
            return;
        }

        final var psiClass = JavaPsiFacade.getInstance(project)
                .findClass(O02_USUARIOS_CALLBACK_FQN, GlobalSearchScope.projectScope(project));
        if (psiClass == null) {
            Messages.showErrorDialog(
                    project,
                    "Classe nao encontrada: " + O02_USUARIOS_CALLBACK_FQN
                            + ". Abra o projeto que contem o modulo jbear-vaadin-admin.",
                    "T3W Productivity"
            );
            return;
        }

        final var containingFile = psiClass.getContainingFile();
        final var vf = containingFile == null ? null : containingFile.getVirtualFile();
        Module module = null;
        if (vf != null) {
            module = ModuleUtilCore.findModuleForFile(vf, project);
        }
        if (module == null) {
            module = ModuleManager.getInstance(project).findModuleByName(HINT_MODULE_NAME);
        }
        if (module == null) {
            Messages.showErrorDialog(
                    project,
                    "Nao foi possivel determinar o modulo IntelliJ para " + O02_USUARIOS_CALLBACK_FQN + ".",
                    "T3W Productivity"
            );
            return;
        }

        final var appType = ConfigurationTypeUtil.findConfigurationType(ApplicationConfigurationType.class);

        final var factory = appType.getConfigurationFactories()[0];
        final var runManager = RunManager.getInstance(project);
        final var settings = runManager.createConfiguration(factory.createTemplateConfiguration(project), factory);
        settings.setName("UsuariosCallback");
        final var config = (ApplicationConfiguration) settings.getConfiguration();
        config.setMainClassName(O02_USUARIOS_CALLBACK_FQN);
        config.setModule(module);
        runManager.setTemporaryConfiguration(settings);

        final var executor = DefaultRunExecutor.getRunExecutorInstance();
        if (executor == null) {
            Messages.showErrorDialog(project, "Executor Run nao disponivel.", "T3W Productivity");
            return;
        }
        ProgramRunnerUtil.executeConfiguration(settings, executor);
    }

}
