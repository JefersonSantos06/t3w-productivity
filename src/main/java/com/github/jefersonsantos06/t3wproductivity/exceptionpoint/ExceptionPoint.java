package com.github.jefersonsantos06.t3wproductivity.exceptionpoint;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

/** Lado do debugger de um {@link ExceptionPointType}: roda na thread do debugger com a thread do app parada no evento. */
final class ExceptionPoint extends LineBreakpoint<JavaLineBreakpointProperties> {

    ExceptionPoint(final Project project, final XBreakpoint<?> breakpoint) {
        super(project, breakpoint);
    }

    /**
     * Mesma lógica da ação nativa "Throw Exception" (ThrowExceptionAction): avalia a expressão no frame e chama
     * {@code ThreadReference.stop}. Com a política "não suspender" (padrão), o debugger retoma a thread logo em seguida
     * e ela lança a exceção antes de executar a linha.
     */
    @Override
    public boolean processLocatableEvent(@NotNull final SuspendContextCommandImpl action, final LocatableEvent event)
            throws EventProcessingException {
        if (!super.processLocatableEvent(action, event)) {
            return false; // condição, filtros ou contagem do breakpoint não bateram
        }
        final String exception = ExceptionPointType.exceptionOf(getXBreakpoint());
        try {
            final var context = action.getSuspendContext();
            final var evaluationContext = new EvaluationContextImpl(context, context.getFrameProxy());
            final ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(myProject, () -> {
                final var position = ContextUtil.getSourcePosition(evaluationContext);
                return EvaluatorBuilderImpl.build(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, exception),
                        ContextUtil.getContextElement(evaluationContext, position), position, myProject);
            });
            if (!(evaluator.evaluate(evaluationContext) instanceof final ObjectReference throwable)) {
                throw new EvaluateException("a expressão não cria um objeto");
            }
            // Efeito colateral da JVM (documentado no README): threads de plataforma ficam com a interrupção ligada.
            event.thread().stop(throwable);
        } catch (final EvaluateException | InvalidTypeException e) {
            throw new EventProcessingException("Erro No Ponto De Exceção", exception + ": " + e.getMessage(), e);
        }
        return true;
    }

    // O "Evaluate and log" guarda a exceção: é lançada, não logada.
    @Override
    protected boolean isLogExpressionEnabled() {
        return false;
    }
}
