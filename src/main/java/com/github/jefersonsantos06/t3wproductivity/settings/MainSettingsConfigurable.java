package com.github.jefersonsantos06.t3wproductivity.settings;

import com.github.jefersonsantos06.t3wproductivity.listeners.ContentDisposable;
import com.github.jefersonsantos06.t3wproductivity.listeners.EnvironmentSettingsListener;
import com.github.jefersonsantos06.t3wproductivity.listeners.FlywaySettingsListener;
import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.services.project.EnvironmentProjectService;
import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class MainSettingsConfigurable implements Configurable {

    private final Project project;
    private SettingsComponent component;

    public MainSettingsConfigurable(final Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "T3W Productivity";
    }

    @Override
    public @NotNull JComponent createComponent() {
        component = new SettingsComponent(project);
        reset();
        return component.getPanel();
    }

    @Override
    public boolean isModified() {
        if (component == null) {
            return false;
        }
        final var flywayService = project.getService(FlywayProjectService.class);
        final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);

        return !flywayService.getMigrationsDirectory().equals(component.getMigrationsDirectory())
               || !appService.getVisibleEnvironmentKeys().equals(component.getVisibleEnvironmentKeys());
    }

    @Override
    public void apply() {
        if (component == null) {
            return;
        }
        final var flywayService = project.getService(FlywayProjectService.class);
        final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
        final var newMigrationsPath = component.getMigrationsDirectory();

        flywayService.setMigrationsDirectory(newMigrationsPath);
        appService.setVisibleEnvironmentKeys(component.getVisibleEnvironmentKeys());

        FlywaySettingsListener.publish(project, newMigrationsPath);
        EnvironmentSettingsListener.publish();
    }

    @Override
    public void reset() {
        if (component == null) {
            return;
        }
        component.refreshMigrationsDirectory();
        component.reloadEnvironmentKeys();
    }

    @Override
    public void disposeUIResources() {
        if (component != null && component.contentDisposable != null) {
            Disposer.dispose(component.contentDisposable);
            component.contentDisposable = null;
        }
        component = null;
    }

    private static final class SettingsComponent {
        private final JPanel panel;
        private final TextFieldWithBrowseButton migrationsDirectoryField;
        private final DefaultListModel<String> availableKeysModel;
        private final DefaultListModel<String> visibleKeysModel;
        private final JBList<String> availableKeysList;
        private final JBList<String> visibleKeysList;
        private final Project project;
        private ContentDisposable contentDisposable;

        public SettingsComponent(Project project) {
            this.project = project;
            this.migrationsDirectoryField = new TextFieldWithBrowseButton();
            this.availableKeysModel = new DefaultListModel<>();
            this.visibleKeysModel = new DefaultListModel<>();
            this.availableKeysList = new JBList<>(availableKeysModel);
            this.visibleKeysList = new JBList<>(visibleKeysModel);
            this.panel = FormBuilder.createFormBuilder()
                    .addLabeledComponent("Pasta de migrations:", migrationsDirectoryField)
                    .addLabeledComponent("Variáveis visíveis na Tool Window (globais; vazio = todas):", createVisibleKeysPanel(), true)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();

            // Outras páginas (ex.: Variáveis de Ambiente) podem aplicar antes desta no mesmo diálogo:
            // só acrescenta chaves novas, sem descartar a seleção ainda não aplicada aqui.
            contentDisposable = new ContentDisposable();
            EnvironmentSettingsListener.subscribe(this.project, contentDisposable, this::addNewAvailableKeys);

            migrationsDirectoryField.addActionListener(event -> chooseMigrationsDirectory());

            availableKeysList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            visibleKeysList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        }

        public JPanel getPanel() {
            return panel;
        }

        public String getMigrationsDirectory() {
            return StringUtils.trimToEmpty(migrationsDirectoryField.getText());
        }

        public void refreshMigrationsDirectory() {
            final var flywayService = project.getService(FlywayProjectService.class);
            migrationsDirectoryField.setText(flywayService.getMigrationsDirectory());
        }

        public List<String> getVisibleEnvironmentKeys() {
            final var visible = new ArrayList<String>();
            for (int i = 0; i < visibleKeysModel.getSize(); i++) {
                visible.add(visibleKeysModel.getElementAt(i));
            }
            return visible;
        }

        public void reloadEnvironmentKeys() {
            final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);

            availableKeysModel.clear();
            visibleKeysModel.clear();

            appService.getVisibleEnvironmentKeys().forEach(visibleKeysModel::addElement);
            addNewAvailableKeys();
        }

        private void addNewAvailableKeys() {
            final var envService = project.getService(EnvironmentProjectService.class);
            envService.getAvailableEnvironmentKeys().stream()
                    .filter(key -> !contains(visibleKeysModel, key) && !contains(availableKeysModel, key))
                    .forEach(availableKeysModel::addElement);
        }

        /**
         * Grava caminho relativo quando a pasta está dentro do projeto, para funcionar em qualquer worktree.
         */
        private void chooseMigrationsDirectory() {
            final var descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            descriptor.setTitle("Pasta De Migrations");
            descriptor.setDescription("Selecione a pasta onde os arquivos Flyway serão criados.");

            final var baseDir = ProjectUtil.guessProjectDir(project);
            final var current = project.getService(FlywayProjectService.class).getMigrationsDirectoryFile(project).orElse(baseDir);
            Optional.ofNullable(FileChooser.chooseFile(descriptor, project, current))
                    .ifPresent(selected -> migrationsDirectoryField.setText(toProjectRelativePath(selected, baseDir)));
        }

        private static String toProjectRelativePath(final VirtualFile selected, final VirtualFile baseDir) {
            final var relative = baseDir == null ? null : VfsUtilCore.getRelativePath(selected, baseDir, '/');
            return StringUtils.isNotEmpty(relative) ? relative : selected.getPath();
        }

        private JPanel createVisibleKeysPanel() {
            final var toVisibleButton = new JButton(">>");
            toVisibleButton.addActionListener(event -> moveSelected(availableKeysList, availableKeysModel, visibleKeysModel));

            final var toAvailableButton = new JButton("<<");
            toAvailableButton.addActionListener(event -> moveSelected(visibleKeysList, visibleKeysModel, availableKeysModel));

            final var buttonSize = JBUI.size(52, 26);
            toVisibleButton.setPreferredSize(buttonSize);
            toAvailableButton.setPreferredSize(buttonSize);
            toVisibleButton.setMaximumSize(buttonSize);
            toAvailableButton.setMaximumSize(buttonSize);

            // Painel dos botões (centralizado verticalmente)
            final var buttonsPanel = new JPanel();
            buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));
            buttonsPanel.add(Box.createVerticalGlue());
            toVisibleButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            buttonsPanel.add(toVisibleButton);
            buttonsPanel.add(Box.createVerticalStrut(JBUI.scale(8)));
            toAvailableButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            buttonsPanel.add(toAvailableButton);
            buttonsPanel.add(Box.createVerticalGlue());

            // Mantém o painel do meio estreito
            final var buttonsPanelWidth = JBUI.scale(70);
            final var buttonsPanelSize = new Dimension(buttonsPanelWidth, Integer.MAX_VALUE);
            buttonsPanel.setMaximumSize(buttonsPanelSize);
            buttonsPanel.setPreferredSize(new Dimension(buttonsPanelWidth, 1));
            buttonsPanel.setMinimumSize(new Dimension(buttonsPanelWidth, 1));

            // Scroll panes com tamanho fixo (evita "dançar")
            final var listPaneSize = JBUI.size(260, 180);
            final var availableScroll = new JBScrollPane(availableKeysList);
            availableScroll.setPreferredSize(listPaneSize);
            availableScroll.setMinimumSize(listPaneSize);
            availableScroll.setMaximumSize(listPaneSize);

            final var visibleScroll = new JBScrollPane(visibleKeysList);
            visibleScroll.setPreferredSize(listPaneSize);
            visibleScroll.setMinimumSize(listPaneSize);
            visibleScroll.setMaximumSize(listPaneSize);

            // Painel principal (3 colunas “fixas” com BoxLayout)
            final var listsPanel = new JPanel();
            listsPanel.setLayout(new BoxLayout(listsPanel, BoxLayout.X_AXIS));
            listsPanel.add(availableScroll);
            listsPanel.add(Box.createHorizontalStrut(JBUI.scale(8)));
            listsPanel.add(buttonsPanel);
            listsPanel.add(Box.createHorizontalStrut(JBUI.scale(8)));
            listsPanel.add(visibleScroll);

            final var mainPanel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
            mainPanel.add(listsPanel, BorderLayout.CENTER);
            return mainPanel;
        }

        private static void moveSelected(final JBList<String> sourceList,
                                         final DefaultListModel<String> sourceModel,
                                         final DefaultListModel<String> targetModel) {
            final var selected = sourceList.getSelectedValuesList();
            if (selected.isEmpty()) {
                return;
            }

            selected.forEach(sourceModel::removeElement);
            selected.forEach(key -> {
                if (!contains(targetModel, key)) {
                    targetModel.addElement(key);
                }
            });
        }

        private static boolean contains(final DefaultListModel<String> model, final String key) {
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).equals(key)) {
                    return true;
                }
            }
            return false;
        }
    }
}
