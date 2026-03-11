package com.github.jefersonsantos06.t3wproductivity.actions;

import com.github.jefersonsantos06.t3wproductivity.ui.FlywayDialog;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class FlywayMigrationCreateAction extends AnAction {

    @Override
    public void actionPerformed(final AnActionEvent event) {
        Optional.ofNullable(event.getProject())
                .ifPresent(FlywayDialog::run);
    }

    @Override
    public void update(final AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(
                event.getProject() != null
        );
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
