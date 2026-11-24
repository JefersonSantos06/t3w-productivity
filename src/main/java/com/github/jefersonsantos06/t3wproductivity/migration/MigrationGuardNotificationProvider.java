package com.github.jefersonsantos06.t3wproductivity.migration;

import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.github.jefersonsantos06.t3wproductivity.ui.FlywayDialog;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Faixa de aviso no topo de uma migration. Só informa e oferece atalhos; nada é bloqueado.
 */
public final class MigrationGuardNotificationProvider implements EditorNotificationProvider, DumbAware {

    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(
            @NotNull final Project project, @NotNull final VirtualFile file) {
        final var service = MigrationGuardService.getInstance(project);
        if (!service.isMigrationFile(file)) {
            return null;
        }
        final var document = FileDocumentManager.getInstance().getDocument(file);
        final var warnings = service.warningsFor(file, document == null ? "" : document.getText());
        if (warnings.isEmpty()) {
            return null;
        }
        return fileEditor -> createPanel(project, file, fileEditor, warnings);
    }

    private static EditorNotificationPanel createPanel(final Project project, final VirtualFile file, final FileEditor fileEditor,
                                                       final List<MigrationGuardRules.Warning> warnings) {
        final var duplicate = warnings.stream().anyMatch(warning -> warning.kind() == MigrationGuardRules.Kind.DUPLICATE_VERSION);
        final var panel = new EditorNotificationPanel(fileEditor,
                duplicate ? EditorNotificationPanel.Status.Error : EditorNotificationPanel.Status.Warning);
        panel.setText(warnings.stream()
                .map(warning -> StringUtil.escapeXmlEntities(warning.message()))
                .collect(Collectors.joining("<br>", "<html>", "</html>")));

        final var kinds = warnings.stream().map(MigrationGuardRules.Warning::kind).toList();
        if (kinds.contains(MigrationGuardRules.Kind.MERGED_EDITED)) {
            panel.createActionLabel("Criar nova migration", () -> FlywayDialog.run(project));
            panel.createActionLabel("Ver diferença com a base", () -> showDiff(project, file));
        }
        if (kinds.contains(MigrationGuardRules.Kind.OUT_OF_ORDER) || duplicate) {
            panel.createActionLabel("Renumerar para agora", () -> renumber(project, file));
        }
        panel.createActionLabel("Ocultar", () -> MigrationGuardService.getInstance(project).dismiss(file));
        return panel;
    }

    private static void showDiff(final Project project, final VirtualFile file) {
        final var service = MigrationGuardService.getInstance(project);
        service.baseContent(file).ifPresent(baseText -> {
            final var contents = DiffContentFactory.getInstance();
            DiffManager.getInstance().showDiff(project, new SimpleDiffRequest(file.getName(),
                    contents.create(project, baseText, file.getFileType()), contents.create(project, file),
                    service.baseRef(), "Atual"));
        });
    }

    private static void renumber(final Project project, final VirtualFile file) {
        try {
            project.getService(FlywayProjectService.class).adjustMigrationName(project, file);
        } catch (final IOException exception) {
            Messages.showErrorDialog(project, exception.getMessage(), "Erro Ao Renumerar Migration");
        }
    }
}
