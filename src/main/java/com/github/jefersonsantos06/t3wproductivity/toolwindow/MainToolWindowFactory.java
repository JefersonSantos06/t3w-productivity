package com.github.jefersonsantos06.t3wproductivity.toolwindow;

import com.github.jefersonsantos06.t3wproductivity.listeners.ContentDisposable;
import com.github.jefersonsantos06.t3wproductivity.listeners.FlywaySettingsListener;
import com.github.jefersonsantos06.t3wproductivity.run.FixedMainClassRunner;
import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.github.jefersonsantos06.t3wproductivity.ui.FlywayDialog;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class MainToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, ToolWindow toolWindow) {
        final var contentDisposable = new ContentDisposable();
        final var content = ContentFactory.getInstance().createContent(createPanel(project, contentDisposable), "", false);
        content.setDisposer(contentDisposable);
        toolWindow.getContentManager().addContent(content);
    }

    static JPanel createPanel(final Project project, final Disposable disposable) {
        final var migrationService = project.getService(FlywayProjectService.class);

        final var migrationsDirectoryField = new JTextField();
        migrationsDirectoryField.setEditable(false);
        migrationsDirectoryField.setText(migrationService.getMigrationsDirectory());

        FlywaySettingsListener.subscribe(project, disposable, migrationsDirectoryField::setText);

        final var runUsuariosCallbackButton = new JButton("Executar UsuariosCallback");
        runUsuariosCallbackButton.addActionListener(event -> FixedMainClassRunner.runO02UsuariosCallback(project));

        final var actionsUsuarios = new JPanel(new GridLayout(1, 1, 8, 0));
        actionsUsuarios.add(runUsuariosCallbackButton);

        final var revealLatestMigrationButton = new JButton("Ver última migration");
        revealLatestMigrationButton.addActionListener(event -> revealLatestMigration(project, migrationService));

        final var createMigrationButton = new JButton("Criar Migration");
        createMigrationButton.addActionListener(event -> FlywayDialog.run(project));

        final var actionsPanel2 = new JPanel(new GridLayout(1, 2, 8, 0));
        actionsPanel2.add(createMigrationButton);
        actionsPanel2.add(revealLatestMigrationButton);

        final var environmentPanel = new EnvironmentPanel(project, disposable);

        final var header = FormBuilder.createFormBuilder()
                .addLabeledComponent("Gerenciamento de usuários", actionsUsuarios, true)
                .addLabeledComponent("Pasta de migrations:", migrationsDirectoryField, true)
                .addComponent(actionsPanel2)
                .setHorizontalGap(10)
                .setVerticalGap(10)
                .addLabeledComponent("Preset de variáveis:", environmentPanel.getPresetComponent(), true)
                .addComponent(new JBLabel("Variáveis de ambiente:"))
                .getPanel();

        // Linhas curtas: uma label HTML longa aumenta a largura mínima do painel.
        final var envDescription = new JBLabel(
                "<html>Prioridade: Projeto > Preset > Global.<br>Em negrito: overrides deste projeto.</html>"
        );
        envDescription.setForeground(UIUtil.getContextHelpForeground());

        // BorderLayout: a tabela ocupa todo o espaço restante em qualquer tamanho da Tool Window.
        // Com o FormBuilder (GridBagLayout) ela encolhia para o tamanho mínimo e as linhas sumiam.
        final var panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        panel.add(header, BorderLayout.NORTH);
        panel.add(environmentPanel.getTableComponent(), BorderLayout.CENTER);
        panel.add(envDescription, BorderLayout.SOUTH);
        panel.setBorder(JBUI.Borders.empty(12));
        return panel;
    }

    /**
     * Seleciona na Project View a migration de maior versão; sem migrations, seleciona a pasta.
     */
    private static void revealLatestMigration(final Project project, final FlywayProjectService migrationService) {
        final var target = migrationService.findLatestMigrationFile(project)
                .or(() -> migrationService.getMigrationsDirectoryFile(project));
        if (target.isEmpty()) {
            Messages.showWarningDialog(project, "Não foi possível localizar a pasta configurada no projeto.", "Flyway");
            return;
        }

        final var file = target.get();
        final Runnable select = () -> ProjectView.getInstance(project).select(null, file, true);
        final var projectToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
        if (projectToolWindow == null) {
            select.run();
        } else {
            projectToolWindow.activate(select);
        }
    }
}
