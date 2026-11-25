package com.github.jefersonsantos06.t3wproductivity.exceptionpoint;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaBreakpointHandler;
import com.intellij.debugger.engine.JavaBreakpointHandlerFactory;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import javax.swing.Icon;

/**
 * Ponto de exceção: line breakpoint Java que, quando atingido, lança na thread a exceção configurada e segue sem
 * pausar. Liga e desliga pelo checkbox na janela Breakpoints, como qualquer breakpoint.
 */
public final class ExceptionPointType extends JavaLineBreakpointType {

    public static final String TITLE = "Pontos De Exceção T3W";
    public static final String DEFAULT_EXCEPTION = "new RuntimeException(\"T3W: exceção de teste\")";

    public ExceptionPointType() {
        super("t3w-exception-point", TITLE);
    }

    /** A expressão fica no "Evaluate and log" do breakpoint, como num logpoint; o {@link ExceptionPoint} lança em vez de logar. */
    static @NotNull String exceptionOf(@NotNull final XBreakpoint<?> breakpoint) {
        final var expression = breakpoint.getLogExpressionObject();
        return expression == null || expression.getExpression().isBlank() ? DEFAULT_EXCEPTION : expression.getExpression();
    }

    /** Onde um line breakpoint Java pode ficar (a ação usa; o {@link #canPutAt} é sempre falso). */
    boolean canAddAt(@NotNull final VirtualFile file, final int line, @NotNull final Project project) {
        return super.canPutAt(file, line, project);
    }

    // Só a ação cria pontos de exceção: o clique no gutter continua criando o breakpoint Java comum.
    @Override
    public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull final Project project) {
        return false;
    }

    @Override
    public @NotNull Breakpoint<JavaLineBreakpointProperties> createJavaBreakpoint(final Project project,
                                                                               final XBreakpoint<JavaLineBreakpointProperties> breakpoint) {
        final var exceptionPoint = new ExceptionPoint(project, breakpoint);
        exceptionPoint.init();
        return exceptionPoint;
    }

    @Override
    public String getDisplayName() {
        return TITLE;
    }

    @Override
    public @NotNull Icon getEnabledIcon() {
        return AllIcons.Debugger.Db_exception_breakpoint;
    }

    @Override
    public @NotNull Icon getSuspendNoneIcon() {
        return AllIcons.Debugger.Db_exception_breakpoint;
    }

    @Override
    public @NotNull Icon getDisabledIcon() {
        return AllIcons.Debugger.Db_disabled_exception_breakpoint;
    }

    /** Faz o debugger Java registrar os pontos de exceção na sessão, como faz com os line breakpoints. */
    public static final class HandlerFactory implements JavaBreakpointHandlerFactory {

        @Override
        public JavaBreakpointHandler createHandler(final DebugProcessImpl process) {
            return new JavaBreakpointHandler(ExceptionPointType.class, process);
        }
    }
}
