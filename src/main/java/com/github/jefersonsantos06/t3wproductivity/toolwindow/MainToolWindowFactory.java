package com.github.jefersonsantos06.t3wproductivity.toolwindow;

import com.github.jefersonsantos06.t3wproductivity.listeners.ContentDisposable;
import com.github.jefersonsantos06.t3wproductivity.listeners.EnvironmentSettingsListener;
import com.github.jefersonsantos06.t3wproductivity.listeners.FlywaySettingsListener;
import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentVariable;
import com.github.jefersonsantos06.t3wproductivity.run.FixedMainClassRunner;
import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.services.project.EnvironmentProjectService;
import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.github.jefersonsantos06.t3wproductivity.ui.FlywayDialog;
import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

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
        final var envService = project.getService(EnvironmentProjectService.class);
        final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);

        final var migrationsDirectoryField = new JTextField();
        migrationsDirectoryField.setEditable(false);
        migrationsDirectoryField.setText(migrationService.getMigrationsDirectory());

        final var envModel = new VisibleEnvironmentTableModel((key, value) -> {
            final var projectEnv = envService.getProjectEnvironment();
            projectEnv.put(key, value);
            envService.setProjectEnvironment(projectEnv);
            envService.syncVisibleEnvironmentKeys(envService.getAvailableEnvironmentKeys(appService.getGlobalEnvironment()));
            EnvironmentSettingsListener.publish(project);
        });
        refreshVisibleEnvironmentRows(envService, appService, envModel);

        FlywaySettingsListener.subscribe(project, disposable, migrationsDirectoryField::setText);
        EnvironmentSettingsListener.subscribe(project, disposable, () ->
                refreshVisibleEnvironmentRows(envService, appService, envModel)
        );

        final var runUsuariosCallbackButton = new JButton("Executar UsuariosCallback");
        runUsuariosCallbackButton.addActionListener(event -> FixedMainClassRunner.runO02UsuariosCallback(project));

        final var actionsUsuarios = new JPanel(new GridLayout(1, 1, 8, 0));
        actionsUsuarios.add(runUsuariosCallbackButton);

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

        final var envDescription = new JBLabel(
                "<html>Qualquer alteração nas variaveis feita aqui afetará apenas a configuração do projeto.</html>"
        );
        envDescription.setForeground(UIUtil.getContextHelpForeground());

        final var panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Gerenciamento de usuarios", actionsUsuarios, true)
                .addLabeledComponent("Pasta de migrations:", migrationsDirectoryField, true)
                .addComponent(actionsPanel2)
                .setHorizontalGap(10)
                .setVerticalGap(10)
                .addLabeledComponent("Variaveis de ambiente:", new JBScrollPane(new JBTable(envModel)), true)
                .addComponent(envDescription)
                .addComponentFillVertically(new JPanel(), 1)
                .getPanel();

        panel.setBorder(JBUI.Borders.empty(12));
        return panel;
    }

    private static void refreshVisibleEnvironmentRows(final EnvironmentProjectService projectService,
                                                      final EnvironmentApplicationService appService,
                                                      final VisibleEnvironmentTableModel model) {
        final var effectiveEnv = EnvironmentVariablesUtil.mergeEnvironment(
                appService.getGlobalEnvironment(),
                projectService.getProjectEnvironment()
        );
        final var rows = new ArrayList<EnvironmentVariable>();
        projectService.getVisibleEnvironmentKeys().forEach(key -> {
            if (effectiveEnv.containsKey(key)) {
                rows.add(new EnvironmentVariable(key, effectiveEnv.get(key)));
            }
        });
        model.setRows(rows);
    }

    private static final class VisibleEnvironmentTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Chave", "Valor" };
        private final List<EnvironmentVariable> rows = new ArrayList<>();
        private final BiConsumer<String, String> valueUpdateConsumer;

        private VisibleEnvironmentTableModel(final BiConsumer<String, String> valueUpdateConsumer) {
            this.valueUpdateConsumer = valueUpdateConsumer;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(final int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final var row = rows.get(rowIndex);
            return columnIndex == 0 ? row.getKey() : row.getValue();
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return columnIndex == 1;
        }

        @Override
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            if (columnIndex != 1) {
                return;
            }

            final var row = rows.get(rowIndex);
            final var value = aValue == null ? "" : aValue.toString();
            row.setValue(value);
            valueUpdateConsumer.accept(row.getKey(), value);
            fireTableRowsUpdated(rowIndex, rowIndex);
        }

        private void setRows(final List<EnvironmentVariable> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }
    }

}
