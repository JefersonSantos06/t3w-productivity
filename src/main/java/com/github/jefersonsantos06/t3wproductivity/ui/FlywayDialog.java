package com.github.jefersonsantos06.t3wproductivity.ui;

import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.github.jefersonsantos06.t3wproductivity.util.FlywayUtils;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.IOException;

public final class FlywayDialog extends DialogWrapper {
    private final JBTextField migrationDescriptionField = new JBTextField();
    private final JBLabel fileNamePreview = new JBLabel();

    private FlywayDialog(Project project) {
        super(project);
        setTitle("Criar Migration Flyway");
        migrationDescriptionField.setMinimumSize(new Dimension(240, 30));
        migrationDescriptionField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull final DocumentEvent e) {
                updateFileNamePreview();
            }
        });
        fileNamePreview.setForeground(UIUtil.getContextHelpForeground());
        updateFileNamePreview();
        init();
    }

    public String getMigrationDescription() {
        return StringUtils.trimToEmpty(migrationDescriptionField.getText());
    }

    private void updateFileNamePreview() {
        final var description = FlywayUtils.normalizeDescription(migrationDescriptionField.getText());
        fileNamePreview.setText(description.isBlank()
                ? " "
                : FlywayUtils.buildSqlFileName(FlywayUtils.buildVersionPrefix(description)));
    }

    @Override
    protected JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Descrição:", migrationDescriptionField, true)
                .addComponentToRightColumn(fileNamePreview)
                .getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return migrationDescriptionField;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (FlywayUtils.normalizeDescription(migrationDescriptionField.getText()).isBlank()) {
            return new ValidationInfo("Informe uma descrição com letras ou números.", migrationDescriptionField);
        }
        return null;
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

        final var migrationService = project.getService(FlywayProjectService.class);
        try {
            final var file = migrationService.createMigration(project, dialog.getMigrationDescription());
            FileEditorManager.getInstance(project).openFile(file, true);
        } catch (final IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), "Erro Ao Criar Migration");
        }
    }
}
