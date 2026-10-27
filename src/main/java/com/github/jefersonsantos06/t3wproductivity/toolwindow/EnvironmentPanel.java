package com.github.jefersonsantos06.t3wproductivity.toolwindow;

import com.github.jefersonsantos06.t3wproductivity.listeners.EnvironmentSettingsListener;
import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentOrigin;
import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentPreset;
import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.services.project.EnvironmentProjectService;
import com.github.jefersonsantos06.t3wproductivity.settings.EnvPresetsConfigurable;
import com.github.jefersonsantos06.t3wproductivity.ui.AddEnvironmentVariableDialog;
import com.github.jefersonsantos06.t3wproductivity.ui.PresetNameValidator;
import com.github.jefersonsantos06.t3wproductivity.ui.SuggestEnvironmentVariableAction;
import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Seção de variáveis da Tool Window: seleção do preset ativo e tabela com o ambiente efetivo do projeto.
 */
final class EnvironmentPanel {

    private static final PresetItem NO_PRESET = new PresetItem(null, "(Nenhum)");

    private final Project project;
    private final EnvironmentProjectService projectService;
    private final EnvironmentApplicationService appService;
    private final DefaultComboBoxModel<PresetItem> presetModel = new DefaultComboBoxModel<>();
    private final EffectiveEnvironmentTableModel tableModel = new EffectiveEnvironmentTableModel();
    private final JBTable table = new JBTable(tableModel);
    private final JPanel presetComponent;
    private final JPanel tableComponent;
    private boolean updatingPresets;

    EnvironmentPanel(final Project project, final Disposable disposable) {
        this.project = project;
        this.projectService = project.getService(EnvironmentProjectService.class);
        this.appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);

        final var presetCombo = new ComboBox<>(presetModel);
        presetCombo.addActionListener(event -> onPresetSelected());
        final var managePresetsLink = new ActionLink("Gerenciar", (ActionListener) event ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, EnvPresetsConfigurable.class));
        presetComponent = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        presetComponent.add(presetCombo, BorderLayout.CENTER);
        presetComponent.add(managePresetsLink, BorderLayout.EAST);

        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new OriginAwareRenderer());
        table.getEmptyText()
                .setText("Nenhuma variável definida")
                .appendSecondaryText("Adicionar variável", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, event -> addVariable());
        tableComponent = ToolbarDecorator.createDecorator(table)
                .setAddAction(button -> addVariable())
                .setAddActionName("Adicionar Variável")
                .disableRemoveAction()
                .disableUpDownActions()
                .addExtraAction(new RemoveOverrideAction())
                .addExtraAction(new SaveOverridesAsPresetAction())
                .createPanel();

        EnvironmentSettingsListener.subscribe(project, disposable, this::refresh);
        refresh();
    }

    JComponent getPresetComponent() {
        return presetComponent;
    }

    JComponent getTableComponent() {
        return tableComponent;
    }

    private void refresh() {
        refreshPresets();
        refreshRows();
    }

    private void refreshPresets() {
        updatingPresets = true;
        try {
            presetModel.removeAllElements();
            presetModel.addElement(NO_PRESET);
            final var activePresetId = projectService.getActivePresetId();
            var selected = NO_PRESET;
            for (final var preset : appService.getPresets()) {
                final var item = new PresetItem(preset.getId(), preset.getName());
                presetModel.addElement(item);
                if (preset.getId().equals(activePresetId)) {
                    selected = item;
                }
            }
            presetModel.setSelectedItem(selected);
        } finally {
            updatingPresets = false;
        }
    }

    private void refreshRows() {
        TableUtil.stopEditing(table);
        final var effectiveEnv = projectService.getEffectiveEnvironment(true);
        final var origins = projectService.getEnvironmentOrigins();
        final var visibleKeys = appService.getVisibleEnvironmentKeys();
        final var keys = visibleKeys.isEmpty() ? effectiveEnv.keySet() : visibleKeys;

        final var rows = new ArrayList<Row>();
        keys.forEach(key -> {
            if (effectiveEnv.containsKey(key)) {
                rows.add(new Row(key, effectiveEnv.get(key), origins.getOrDefault(key, EnvironmentOrigin.GLOBAL)));
            }
        });
        tableModel.setRows(rows);
    }

    private void onPresetSelected() {
        if (updatingPresets) {
            return;
        }
        final var item = (PresetItem) presetModel.getSelectedItem();
        final var presetId = item == null ? null : item.id();
        if (Objects.equals(presetId, projectService.getActivePresetId())) {
            return;
        }
        projectService.setActivePresetId(presetId);
        refreshRows();
    }

    private void addVariable() {
        TableUtil.stopEditing(table);

        final var shownKeys = tableModel.rows.stream().map(row -> row.key).collect(Collectors.toSet());
        final var suggestions = new LinkedHashSet<>(projectService.getAvailableEnvironmentKeys());
        suggestions.addAll(SuggestEnvironmentVariableAction.SUGGESTED_ENV_KEYS);
        suggestions.removeAll(shownKeys);

        final var projectKeys = projectService.getProjectEnvironment().keySet();
        final var keysOverridingGlobal = new HashSet<>(projectKeys);
        projectService.getActivePreset().ifPresent(preset -> keysOverridingGlobal.addAll(preset.getVariables().keySet()));

        final var dialog = new AddEnvironmentVariableDialog(
                project,
                suggestions,
                projectKeys,
                appService.getGlobalEnvironment().keySet(),
                keysOverridingGlobal
        );
        if (!dialog.showAndGet()) {
            return;
        }

        final var key = dialog.getKey();
        if (dialog.isGlobal()) {
            final var globalEnv = appService.getGlobalEnvironment();
            globalEnv.put(key, dialog.getValue());
            appService.setGlobalEnvironment(globalEnv);
        } else {
            projectService.setProjectOverride(key, dialog.getValue());
        }

        // Com a lista de visíveis configurada, a chave nova ficaria escondida.
        final var visibleKeys = new ArrayList<>(appService.getVisibleEnvironmentKeys());
        if (!visibleKeys.isEmpty() && !visibleKeys.contains(key)) {
            visibleKeys.add(key);
            appService.setVisibleEnvironmentKeys(visibleKeys);
        }

        EnvironmentSettingsListener.publish();
        selectKey(key);
    }

    private void selectKey(final String key) {
        for (int i = 0; i < tableModel.rows.size(); i++) {
            if (tableModel.rows.get(i).key.equals(key)) {
                final var viewRow = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(viewRow, viewRow);
                table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                return;
            }
        }
    }

    private Row getSelectedRow() {
        final var selected = table.getSelectedRow();
        return selected < 0 ? null : tableModel.rows.get(table.convertRowIndexToModel(selected));
    }

    private record PresetItem(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final class Row {
        private final String key;
        private String value;
        private EnvironmentOrigin origin;

        private Row(final String key, final String value, final EnvironmentOrigin origin) {
            this.key = key;
            this.value = value;
            this.origin = origin;
        }
    }

    private final class EffectiveEnvironmentTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Chave", "Valor", "Origem" };
        private static final int VALUE_COLUMN = 1;
        private final List<Row> rows = new ArrayList<>();

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
            return switch (columnIndex) {
                case 0 -> row.key;
                case VALUE_COLUMN -> row.value;
                default -> row.origin.getLabel();
            };
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return columnIndex == VALUE_COLUMN;
        }

        /**
         * Editar um valor cria (ou atualiza) o override deste projeto.
         */
        @Override
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            if (columnIndex != VALUE_COLUMN) {
                return;
            }

            final var row = rows.get(rowIndex);
            final var value = aValue == null ? "" : aValue.toString();
            if (value.equals(row.value)) {
                return;
            }
            projectService.setProjectOverride(row.key, value);
            row.value = value;
            row.origin = EnvironmentOrigin.PROJECT;
            fireTableRowsUpdated(rowIndex, rowIndex);
        }

        private void setRows(final List<Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }
    }

    private final class OriginAwareRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected,
                                                       final boolean hasFocus, final int row, final int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            final var origin = tableModel.rows.get(table.convertRowIndexToModel(row)).origin;
            setFont(table.getFont().deriveFont(origin == EnvironmentOrigin.PROJECT ? Font.BOLD : Font.PLAIN));
            if (!isSelected && table.convertColumnIndexToModel(column) == 2) {
                setForeground(UIUtil.getContextHelpForeground());
            }
            return this;
        }
    }

    private final class RemoveOverrideAction extends DumbAwareAction {

        private RemoveOverrideAction() {
            super("Remover Override", "Remove o override do projeto e volta ao valor do preset/global", AllIcons.Actions.Rollback);
        }

        @Override
        public void actionPerformed(@NotNull final AnActionEvent e) {
            final var row = getSelectedRow();
            if (row == null) {
                return;
            }
            TableUtil.stopEditing(table);
            projectService.removeProjectOverride(row.key);
            refreshRows();
        }

        @Override
        public void update(@NotNull final AnActionEvent e) {
            final var row = getSelectedRow();
            e.getPresentation().setEnabled(row != null && row.origin == EnvironmentOrigin.PROJECT);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Cria um preset com o preset ativo + overrides do projeto, ativa-o e limpa os overrides.
     * O ambiente efetivo não muda, e o preset passa a poder ser escolhido em outras worktrees.
     */
    private final class SaveOverridesAsPresetAction extends DumbAwareAction {

        private SaveOverridesAsPresetAction() {
            super("Salvar Overrides Como Preset", "Cria um preset global com o preset ativo + overrides deste projeto", AllIcons.Actions.MenuSaveall);
        }

        @Override
        public void actionPerformed(@NotNull final AnActionEvent e) {
            TableUtil.stopEditing(table);
            final var overrides = projectService.getProjectEnvironment();
            if (overrides.isEmpty()) {
                return;
            }

            final var presets = appService.getPresets();
            final var name = Messages.showInputDialog(
                    project,
                    "Nome do novo preset (preset ativo + overrides deste projeto):",
                    "Salvar Overrides Como Preset",
                    null,
                    "",
                    new PresetNameValidator(presets, null)
            );
            if (name == null) {
                return;
            }

            final var baseVariables = projectService.getActivePreset()
                    .map(EnvironmentPreset::getVariables)
                    .orElseGet(Map::of);
            final var preset = EnvironmentPreset.create(name.trim(), EnvironmentVariablesUtil.mergeEnvironment(true, baseVariables, overrides));
            presets.add(preset);
            appService.setPresets(presets);
            projectService.setActivePresetId(preset.getId());
            projectService.setProjectEnvironment(Map.of());
            EnvironmentSettingsListener.publish();
        }

        @Override
        public void update(@NotNull final AnActionEvent e) {
            e.getPresentation().setEnabled(!projectService.getProjectEnvironment().isEmpty());
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }
}
