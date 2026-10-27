package com.github.jefersonsantos06.t3wproductivity.settings;

import com.github.jefersonsantos06.t3wproductivity.listeners.EnvironmentSettingsListener;
import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentPreset;
import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.ui.EnvironmentTableModel;
import com.github.jefersonsantos06.t3wproductivity.ui.EnvironmentTablePanel;
import com.github.jefersonsantos06.t3wproductivity.ui.PresetNameValidator;
import com.github.jefersonsantos06.t3wproductivity.util.AmbientePresetUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Presets globais de variáveis de ambiente, selecionáveis por projeto na Tool Window.
 */
public final class EnvPresetsConfigurable implements Configurable {

    private final Project project;
    private PresetsComponent component;

    public EnvPresetsConfigurable(final Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "Presets de Variáveis";
    }

    @Override
    public JComponent createComponent() {
        component = new PresetsComponent(project);
        reset();
        return component.panel;
    }

    @Override
    public boolean isModified() {
        return component != null && !getApplicationService().getPresets().equals(component.getPresets());
    }

    @Override
    public void apply() {
        if (component == null) {
            return;
        }
        getApplicationService().setPresets(component.getPresets());
        EnvironmentSettingsListener.publish();
    }

    @Override
    public void reset() {
        if (component == null) {
            return;
        }
        component.setPresets(getApplicationService().getPresets());
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }

    private static EnvironmentApplicationService getApplicationService() {
        return ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
    }

    private static final class PresetsComponent {
        private static final String TABLE_CARD = "table";
        private static final String EMPTY_CARD = "empty";
        private static final String GENERATE_TITLE = "Gerar Presets do Ambiente";

        private final Project project;
        private final JPanel panel;
        private final DefaultListModel<EnvironmentPreset> presetsModel = new DefaultListModel<>();
        private final JBList<EnvironmentPreset> presetsList = new JBList<>(presetsModel);
        private final EnvironmentTableModel variablesModel = new EnvironmentTableModel();
        private final CardLayout variablesCards = new CardLayout();
        private final JPanel variablesPanel = new JPanel(variablesCards);
        private boolean loadingVariables;

        private PresetsComponent(final Project project) {
            this.project = project;
            presetsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            presetsList.setCellRenderer(SimpleListCellRenderer.create("", EnvironmentPreset::getName));
            presetsList.addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    loadSelectedVariables();
                }
            });
            variablesModel.addTableModelListener(event -> storeSelectedVariables());

            final var presetsPanel = ToolbarDecorator.createDecorator(presetsList)
                    .setAddAction(button -> addPreset())
                    .setRemoveAction(button -> removeSelectedPreset())
                    .setEditAction(button -> renameSelectedPreset())
                    .setEditActionName("Renomear")
                    .addExtraAction(new DuplicatePresetAction())
                    .addExtraAction(new GenerateAmbientePresetsAction())
                    .createPanel();

            final var emptyLabel = new JBLabel("Crie ou selecione um preset.", SwingConstants.CENTER);
            emptyLabel.setForeground(UIUtil.getContextHelpForeground());
            variablesPanel.add(EnvironmentTablePanel.create("Variáveis do preset", variablesModel), TABLE_CARD);
            variablesPanel.add(emptyLabel, EMPTY_CARD);

            final var splitter = new JBSplitter(false, 0.3f);
            splitter.setFirstComponent(presetsPanel);
            splitter.setSecondComponent(variablesPanel);

            final var description = new JBLabel(
                    "<html>Presets são globais. Cada projeto/worktree escolhe o preset ativo na Tool Window."
                    + "<br>Prioridade: Override do projeto > Preset > Global.</html>"
            );
            description.setForeground(UIUtil.getContextHelpForeground());

            panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
            panel.add(description, BorderLayout.NORTH);
            panel.add(splitter, BorderLayout.CENTER);
            loadSelectedVariables();
        }

        private List<EnvironmentPreset> getPresets() {
            final var presets = new ArrayList<EnvironmentPreset>();
            for (int i = 0; i < presetsModel.getSize(); i++) {
                presets.add(presetsModel.getElementAt(i).copy());
            }
            return presets;
        }

        private void setPresets(final List<EnvironmentPreset> presets) {
            presetsModel.clear();
            presets.forEach(preset -> presetsModel.addElement(preset.copy()));
            if (!presetsModel.isEmpty()) {
                presetsList.setSelectedIndex(0);
            }
            loadSelectedVariables();
        }

        private void loadSelectedVariables() {
            final var selected = presetsList.getSelectedValue();
            loadingVariables = true;
            try {
                variablesModel.setRows(selected == null ? Map.of() : selected.getVariables());
            } finally {
                loadingVariables = false;
            }
            variablesCards.show(variablesPanel, selected == null ? EMPTY_CARD : TABLE_CARD);
        }

        private void storeSelectedVariables() {
            final var selected = presetsList.getSelectedValue();
            if (loadingVariables || selected == null) {
                return;
            }
            selected.setVariables(variablesModel.toMap());
        }

        private void addPreset() {
            final var name = askPresetName("Novo Preset", "", null);
            if (name == null) {
                return;
            }
            addAndSelect(EnvironmentPreset.create(name, Map.of()));
        }

        private void renameSelectedPreset() {
            final var index = presetsList.getSelectedIndex();
            if (index < 0) {
                return;
            }
            final var preset = presetsModel.getElementAt(index);
            final var name = askPresetName("Renomear Preset", preset.getName(), preset.getId());
            if (name == null) {
                return;
            }
            preset.setName(name);
            presetsModel.set(index, preset);
        }

        private void removeSelectedPreset() {
            final var index = presetsList.getSelectedIndex();
            if (index < 0) {
                return;
            }
            presetsModel.remove(index);
            if (!presetsModel.isEmpty()) {
                presetsList.setSelectedIndex(Math.min(index, presetsModel.getSize() - 1));
            }
            loadSelectedVariables();
        }

        private void duplicateSelectedPreset() {
            final var selected = presetsList.getSelectedValue();
            if (selected == null) {
                return;
            }
            final var name = askPresetName("Duplicar Preset", selected.getName() + " (cópia)", null);
            if (name == null) {
                return;
            }
            addAndSelect(EnvironmentPreset.create(name, selected.getVariables()));
        }

        /**
         * Cria/atualiza um preset por ambiente do enum {@code Ambiente} do projeto aberto.
         * As alterações ficam pendentes até Apply/OK, como qualquer outra edição desta página.
         */
        private void generateAmbientePresets() {
            if (DumbService.isDumb(project)) {
                Messages.showWarningDialog(panel, "Aguarde a indexação do projeto terminar.", GENERATE_TITLE);
                return;
            }

            final var ambientes = ReadAction.compute(() -> AmbientePresetUtils.readAmbientes(project));
            if (ambientes.isEmpty()) {
                Messages.showWarningDialog(
                        panel,
                        "Enum Ambiente não encontrado (" + String.join(" ou ", AmbientePresetUtils.AMBIENTE_FQNS) + ")."
                        + "\nAbra este Settings a partir do projeto jbear.",
                        GENERATE_TITLE
                );
                return;
            }

            final var selected = presetsList.getSelectedValue();
            final var selectedId = selected == null ? null : selected.getId();
            final var result = AmbientePresetUtils.mergeAmbientePresets(getPresets(), ambientes.get());

            presetsModel.clear();
            result.presets().forEach(presetsModel::addElement);
            final var selectedIndex = result.presets().stream()
                    .map(EnvironmentPreset::getId)
                    .toList()
                    .indexOf(selectedId);
            if (!presetsModel.isEmpty()) {
                presetsList.setSelectedIndex(Math.max(selectedIndex, 0));
            }
            loadSelectedVariables();

            Messages.showInfoMessage(
                    panel,
                    result.created() + " preset(s) criado(s), " + result.updated() + " atualizado(s).",
                    GENERATE_TITLE
            );
        }

        private void addAndSelect(final EnvironmentPreset preset) {
            presetsModel.addElement(preset);
            presetsList.setSelectedIndex(presetsModel.getSize() - 1);
            loadSelectedVariables();
        }

        private String askPresetName(final String title, final String initialValue, final String ignoredPresetId) {
            final var name = Messages.showInputDialog(
                    panel,
                    "Nome do preset:",
                    title,
                    null,
                    initialValue,
                    new PresetNameValidator(getPresets(), ignoredPresetId)
            );
            return name == null ? null : name.trim();
        }

        private final class DuplicatePresetAction extends DumbAwareAction {

            private DuplicatePresetAction() {
                super("Duplicar", "Duplicar o preset selecionado", AllIcons.Actions.Copy);
            }

            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                duplicateSelectedPreset();
            }

            @Override
            public void update(@NotNull final AnActionEvent e) {
                e.getPresentation().setEnabled(presetsList.getSelectedValue() != null);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        }

        private final class GenerateAmbientePresetsAction extends DumbAwareAction {

            private GenerateAmbientePresetsAction() {
                super(GENERATE_TITLE, "Cria/atualiza um preset por ambiente do enum Ambiente (exceto TDD e DEV)", AllIcons.Nodes.Enum);
            }

            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                generateAmbientePresets();
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        }
    }
}
