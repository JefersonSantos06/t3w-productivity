package com.github.jefersonsantos06.t3wproductivity.whatsnew;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public final class WhatsNewAction extends DumbAwareAction {

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        WhatsNewDialog.showForCurrentVersion(event.getProject());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
