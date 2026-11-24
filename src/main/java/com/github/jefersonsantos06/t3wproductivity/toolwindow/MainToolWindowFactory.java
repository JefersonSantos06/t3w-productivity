package com.github.jefersonsantos06.t3wproductivity.toolwindow;

import com.github.jefersonsantos06.t3wproductivity.ambiente.AmbienteMatrixPanel;
import com.github.jefersonsantos06.t3wproductivity.listeners.ContentDisposable;
import com.github.jefersonsantos06.t3wproductivity.listeners.EnvironmentSettingsListener;
import com.github.jefersonsantos06.t3wproductivity.listeners.FlywaySettingsListener;
import com.github.jefersonsantos06.t3wproductivity.run.FixedMainClassRunner;
import com.github.jefersonsantos06.t3wproductivity.run.WorktreeIsolationSupport;
import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.github.jefersonsantos06.t3wproductivity.ui.FlywayDialog;
import com.github.jefersonsantos06.t3wproductivity.util.WorktreeIsolationUtils;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

public final class MainToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, ToolWindow toolWindow) {
        final var contentManager = toolWindow.getContentManager();
        final var contentDisposable = new ContentDisposable();
        final var content = ContentFactory.getInstance().createContent(createPanel(project, contentDisposable), "Geral", false);
        content.setDisposer(contentDisposable);
        contentManager.addContent(content);

        final var ambientesDisposable = new ContentDisposable();
        final var ambientesPanel = new AmbienteMatrixPanel(project, ambientesDisposable);
        final var ambientesContent = ContentFactory.getInstance().createContent(ambientesPanel.getComponent(), "Ambientes", false);
        ambientesContent.setDisposer(ambientesDisposable);
        contentManager.addContent(ambientesContent);
        // Relê o enum ao abrir a aba, para refletir edições no Ambiente.java.
        contentManager.addContentManagerListener(new ContentManagerListener() {
            @Override
            public void selectionChanged(@NotNull final ContentManagerEvent event) {
                if (event.getContent() == ambientesContent && event.getOperation() == ContentManagerEvent.ContentOperation.add) {
                    ambientesPanel.reload();
                }
            }
        });
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
                .addComponent(createWorktreeComponent(project, disposable))
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
     * Slot da worktree (porta e cookie de sessão deslocados ao rodar apps) com atalho para trocar o número.
     * Só aparece com o isolamento ligado nas configurações.
     */
    private static JComponent createWorktreeComponent(final Project project, final Disposable disposable) {
        final var label = new JBLabel();
        final var changeSlot = new ActionLink("Alterar slot", event -> {
            final var basePath = project.getBasePath();
            if (basePath == null) {
                return;
            }
            final var worktree = WorktreeIsolationUtils.detectWorktree(Path.of(basePath));
            if (!worktree.linked()) {
                Messages.showInfoMessage(project, "O checkout principal usa sempre as portas e cookies originais.", "Worktree");
                return;
            }
            final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
            final var value = Messages.showInputDialog(project, "Slot desta worktree (porta + slot x " + WorktreeIsolationUtils.PORT_STEP + "):",
                    "Alterar Slot", null, String.valueOf(appService.getWorktreeSlot(worktree.path())), new InputValidator() {
                        @Override
                        public boolean checkInput(final String input) {
                            return input != null && input.strip().matches("[1-9]\\d?");
                        }

                        @Override
                        public boolean canClose(final String input) {
                            return checkInput(input);
                        }
                    });
            if (value != null) {
                appService.setWorktreeSlot(worktree.path(), Integer.parseInt(value.strip()));
                label.setText(WorktreeIsolationSupport.describeProject(project));
            }
        });
        final var slotRow = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        slotRow.add(label, BorderLayout.CENTER);
        slotRow.add(changeSlot, BorderLayout.EAST);

        final var component = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        component.add(new JBLabel("Worktree:"), BorderLayout.NORTH);
        component.add(slotRow, BorderLayout.CENTER);

        final Runnable refresh = () -> {
            final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
            component.setVisible(appService.isWorktreeIsolationEnabled());
            label.setText(WorktreeIsolationSupport.describeProject(project));
        };
        refresh.run();
        EnvironmentSettingsListener.subscribe(project, disposable, refresh::run);
        return component;
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
