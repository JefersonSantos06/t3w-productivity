package com.github.jefersonsantos06.t3wproductivity.settings;

import com.github.jefersonsantos06.t3wproductivity.listeners.EnvironmentSettingsListener;
import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentVariable;
import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.services.project.EnvironmentProjectService;
import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class EnvSettingsConfigurable implements Configurable {

    private static final List<String> STATIC_SUGGESTED_ENV_KEYS = List.of(
            "T3W_DEV_DB_NAME",
            "T3W_DEV_DB_NAME_T3W_SITE",
            "T3W_DEV_DB_NAME_VAADIN_TOTEM",
            "T3W_DEV_DB_NAME_PAINEL",
            "T3W_DEV_DB_NAME_ORCAMENTO",
            "T3W_DEV_DB_NAME_CINEMA",
            "T3W_DEV_DB_NAME_EXIBIDOR",
            "T3W_DEV_DB_NAME_ADMIN",
            "T3W_DEV_DB_NAME_API",
            "T3W_DEV_DB_NAME_IMAGEM_SITE",
            "T3W_DEV_DB_NAME_DIREITOS",
            "T3W_DEV_DB_APPNAME_PREFIX"
    );

    private final Project project;
    private SettingsComponent component;

    public EnvSettingsConfigurable(final Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "Variaveis de Ambiente";
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
        projectService.syncVisibleEnvironmentKeys(projectService.getAvailableEnvironmentKeys(appService.getGlobalEnvironment()));

        EnvironmentSettingsListener.publish(project);
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

            final var globalPanel = createTablePanel("Global (Application)", globalModel);
            final var projectPanel = createTablePanel("Projeto (Override)", projectModel);

            final var envPanel = new JPanel(new GridLayout(2, 1, 0, 12));
            envPanel.add(projectPanel);
            envPanel.add(globalPanel);

            this.panel = FormBuilder.createFormBuilder()
                    .addComponent(envPanel)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();
        }

        private static JPanel createTablePanel(final String title, final EnvironmentTableModel model) {
            final var table = new JBTable(model);
            final var decorated = ToolbarDecorator.createDecorator(table)
                    .setAddAction(button -> model.addRow())
                    .setRemoveAction(button -> {
                        final var selected = table.getSelectedRow();
                        if (selected >= 0) {
                            model.removeRow(selected);
                        }
                    })
                    .addExtraAction(new SuggestEnvironmentVariableAction(table, model))
                    .createPanel();

            final var section = new JPanel(new BorderLayout(0, 8));
            section.add(new JLabel(title), BorderLayout.NORTH);
            section.add(decorated, BorderLayout.CENTER);
            return section;
        }
    }

    private static final class SuggestEnvironmentVariableAction extends DumbAwareAction {

        private final JBTable table;
        private final EnvironmentTableModel model;

        private SuggestEnvironmentVariableAction(final JBTable table, final EnvironmentTableModel model) {
            super(AllIcons.Actions.Lightning);
            this.table = table;
            this.model = model;
        }

        @Override
        public void actionPerformed(@NotNull final AnActionEvent e) {
            final var suggestions = getAvailableSuggestions();
            if (suggestions.isEmpty()) {
                return;
            }

            final var selected = (String) JOptionPane.showInputDialog(
                    table,
                    "Selecione uma variavel para adicionar:",
                    "Sugerir variavel de ambiente",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    suggestions.toArray(String[]::new),
                    suggestions.getFirst()
            );
            if (selected == null) {
                return;
            }

            final var insertedRow = model.addRow(selected, "");
            table.getSelectionModel().setSelectionInterval(insertedRow, insertedRow);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(!getAvailableSuggestions().isEmpty());
        }

        private List<String> getAvailableSuggestions() {
            final var currentKeys = model.getNormalizedKeys();
            final var available = new ArrayList<String>();
            for (final var suggestedKey : STATIC_SUGGESTED_ENV_KEYS) {
                if (!currentKeys.contains(suggestedKey)) {
                    available.add(suggestedKey);
                }
            }
            return available;
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    private static final class EnvironmentTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Chave", "Valor" };
        private final List<EnvironmentVariable> rows = new ArrayList<>();

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
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            final var row = rows.get(rowIndex);
            final var value = aValue == null ? "" : aValue.toString();
            if (columnIndex == 0) {
                row.setKey(value);
            } else {
                row.setValue(value);
            }
            fireTableRowsUpdated(rowIndex, rowIndex);
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return true;
        }

        private void setRows(final Map<String, String> source) {
            rows.clear();
            rows.addAll(EnvironmentVariablesUtil.toEntries(source));
            fireTableDataChanged();
        }

        private Map<String, String> toMap() {
            return EnvironmentVariablesUtil.fromEntries(rows);
        }

        private void addRow() {
            rows.add(new EnvironmentVariable());
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        private int addRow(final String key, final String value) {
            rows.add(new EnvironmentVariable(key, value));
            final var insertedRow = rows.size() - 1;
            fireTableRowsInserted(insertedRow, insertedRow);
            return insertedRow;
        }

        private void removeRow(final int row) {
            rows.remove(row);
            fireTableRowsDeleted(row, row);
        }

        private Set<String> getNormalizedKeys() {
            final var keys = new LinkedHashSet<String>();
            for (final var row : rows) {
                final var normalizedKey = StringUtils.trimToNull(row.getKey());
                if (normalizedKey != null) {
                    keys.add(normalizedKey);
                }
            }
            return keys;
        }
    }
}
