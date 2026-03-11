package com.github.jefersonsantos06.t3wproductivity.ui;

import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class FlywayDialog extends DialogWrapper {
    private final JBTextField migrationDescriptionField = new JBTextField();

    private FlywayDialog(Project project) {
        super(project);
        setTitle("Criar Migration Flyway");
        migrationDescriptionField.setMinimumSize(new Dimension(240, 30));
        init();
    }

    public String getMigrationDescription() {
        return StringUtils.trimToEmpty(migrationDescriptionField.getText());
    }

    @Override
    protected JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Descricao:", migrationDescriptionField, true)
                .getPanel();
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{ getOKAction(), getCancelAction() };
    }

    public static void run(final Project project) {
        final var dialog = new FlywayDialog(project);
        if (!dialog.showAndGet()) {
            return;
        }

        final var description = dialog.getMigrationDescription();
        if (description.isBlank()) {
            Messages.showErrorDialog(project, "A descricao da migration nao pode ficar vazia.", "Erro Ao Criar Migration");
            return;
        }

        final var migrationService = project.getService(FlywayProjectService.class);
        try {
            final var file = migrationService.createMigration(project, description);
            FileEditorManager.getInstance(project).openFile(file, true);
            VirtualFileManager.getInstance().syncRefresh();
        } catch (final IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), "Erro Ao Criar Migration");
        }
    }
}
