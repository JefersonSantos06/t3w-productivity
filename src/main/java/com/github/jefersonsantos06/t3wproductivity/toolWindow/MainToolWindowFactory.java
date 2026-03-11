package com.github.jefersonsantos06.t3wproductivity.toolwindow;

import com.github.jefersonsantos06.t3wproductivity.listeners.FlywaySettingsListener;
import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.github.jefersonsantos06.t3wproductivity.ui.FlywayDialog;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
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

    private static JPanel createPanel(final Project project, final Disposable disposable) {
        final var migrationService = project.getService(FlywayProjectService.class);

        final var migrationsDirectoryField = new JTextField();
        migrationsDirectoryField.setEditable(false);
        migrationsDirectoryField.setText(migrationService.getMigrationsDirectory());
        FlywaySettingsListener.subscribe(project,disposable, migrationsDirectoryField::setText);

        final var revealDirectoryButton = new JButton("Ver pasta de migrations");
        revealDirectoryButton.addActionListener(event -> migrationService.getMigrationsDirectoryFile(project)
                .ifPresentOrElse(
                        file -> ProjectView.getInstance(project).select(null, file, true),
                        () -> Messages.showWarningDialog(project, "Nao foi possivel localizar a pasta configurada no projeto.", "Flyway")
                ));

        final var createMigrationButton = new JButton("Criar Migration");
        createMigrationButton.addActionListener(event -> FlywayDialog.run(project));

        final var actionsPanel2 = new JPanel(new GridLayout(1, 2, 8, 0));
        actionsPanel2.add(createMigrationButton);
        actionsPanel2.add(revealDirectoryButton);

        final var panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Pasta de migrations:", migrationsDirectoryField, true)
                .setHorizontalGap(10)
                .setVerticalGap(10)
                .addComponent(actionsPanel2)
                .addComponentFillVertically(new JPanel(), 1)
                .getPanel();

        panel.setBorder(JBUI.Borders.empty(12));
        return panel;
    }

    private static final class ContentDisposable implements Disposable {
        @Override
        public void dispose() {
            // No-op: used to bind listeners lifecycle to ToolWindow content.
        }
    }
}
