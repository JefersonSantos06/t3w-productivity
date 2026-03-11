package com.github.jefersonsantos06.t3wproductivity.actions;

import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Optional;

public final class FlywayMigrationAdjustNameAction extends AnAction {

    @Override
    public void actionPerformed(final AnActionEvent event) {
        final var project = event.getProject();
        final var file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || file == null || file.isDirectory()) {
            return;
        }
        try {
            final var migrationService = project.getService(FlywayProjectService.class);
            migrationService.adjustMigrationName(project, file);
        } catch (final IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), "Erro Ao Ajustar Migration");
        }
    }

    @Override
    public void update(final AnActionEvent event) {
        final var project = event.getProject();
        final var selectedFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        final var file = Optional.ofNullable(selectedFiles)
                .filter(files -> files.length == 1)
                .map(files -> files[0])
                .orElse(null);

        final boolean visible = project != null
                && file != null
                && !file.isDirectory()
                && "sql".equalsIgnoreCase(file.getExtension());

        event.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
