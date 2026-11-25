package com.github.jefersonsantos06.t3wproductivity.exceptionpoint;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

/** Cria (ou edita) o ponto de exceção da linha do cursor. */
public final class AddExceptionPointAction extends AnAction {

    private static final String TITLE = "Adicionar Ponto De Exceção";

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        final var project = event.getProject();
        final var editor = event.getData(CommonDataKeys.EDITOR);
        final var file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || editor == null || file == null) {
            return;
        }
        final int line = editor.getCaretModel().getLogicalPosition().line;
        final var type = XDebuggerUtil.getInstance().findBreakpointType(ExceptionPointType.class);
        if (!type.canAddAt(file, line, project)) {
            Messages.showWarningDialog(project, "Coloque o cursor numa linha com código executável.", TITLE);
            return;
        }
        final var manager = XDebuggerManager.getInstance(project).getBreakpointManager();
        final XLineBreakpoint<JavaLineBreakpointProperties> existing =
                ContainerUtil.getFirstItem(manager.findBreakpointsAtLine(type, file, line));
        final String exception = Messages.showInputDialog(project,
                "Exceção lançada antes de a linha " + (line + 1) + " executar (expressão Java):", TITLE, null,
                existing != null ? ExceptionPointType.exceptionOf(existing) : ExceptionPointType.DEFAULT_EXCEPTION, null);
        if (exception == null || exception.isBlank()) {
            return;
        }
        WriteAction.run(() -> {
            final var breakpoint = existing != null
                    ? existing
                    : manager.addLineBreakpoint(type, file.getUrl(), line, type.createBreakpointProperties(file, line));
            breakpoint.setSuspendPolicy(SuspendPolicy.NONE);
            breakpoint.setLogExpression(exception.strip());
        });
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null
                && event.getData(CommonDataKeys.EDITOR) != null
                && event.getData(CommonDataKeys.PSI_FILE) instanceof PsiJavaFile);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
