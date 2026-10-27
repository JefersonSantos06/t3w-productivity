package com.github.jefersonsantos06.t3wproductivity.settings;

import com.github.jefersonsantos06.t3wproductivity.listeners.EnvironmentSettingsListener;
import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.services.project.EnvironmentProjectService;
import com.github.jefersonsantos06.t3wproductivity.ui.EnvironmentTableModel;
import com.github.jefersonsantos06.t3wproductivity.ui.EnvironmentTablePanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;

public final class EnvSettingsConfigurable implements Configurable {

    private final Project project;
    private SettingsComponent component;

    public EnvSettingsConfigurable(final Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "Variáveis de Ambiente";
    }

    @Override
    public JComponent createComponent() {
        component = new SettingsComponent();
        reset();
        return component.panel;
    }

    @Override
    public boolean isModified() {
        if (component == null) {
            return false;
        }

        final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
        final var projectService = project.getService(EnvironmentProjectService.class);

        final var currentGlobal = appService.getGlobalEnvironment();
        final var currentProject = projectService.getProjectEnvironment();

        return !currentGlobal.equals(component.globalModel.toMap())
               || !currentProject.equals(component.projectModel.toMap());
    }

    @Override
    public void apply() {
        if (component == null) {
            return;
        }

        final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
        final var projectService = project.getService(EnvironmentProjectService.class);

        appService.setGlobalEnvironment(component.globalModel.toMap());
        projectService.setProjectEnvironment(component.projectModel.toMap());

        EnvironmentSettingsListener.publish();
    }

    @Override
    public void reset() {
        if (component == null) {
            return;
        }

        final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
        final var projectService = project.getService(EnvironmentProjectService.class);

        component.globalModel.setRows(appService.getGlobalEnvironment());
        component.projectModel.setRows(projectService.getProjectEnvironment());
    }

    private static final class SettingsComponent {
        private final JPanel panel;
        private final EnvironmentTableModel globalModel;
        private final EnvironmentTableModel projectModel;

        private SettingsComponent() {
            this.globalModel = new EnvironmentTableModel();
            this.projectModel = new EnvironmentTableModel();

            final var globalPanel = EnvironmentTablePanel.create("Global (todos os projetos)", globalModel);
            final var projectPanel = EnvironmentTablePanel.create("Projeto (override, só este projeto)", projectModel);

            final var envPanel = new JPanel(new GridLayout(2, 1, 0, 12));
            envPanel.add(projectPanel);
            envPanel.add(globalPanel);

            this.panel = FormBuilder.createFormBuilder()
                    .addComponent(envPanel)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();
        }
    }
}
