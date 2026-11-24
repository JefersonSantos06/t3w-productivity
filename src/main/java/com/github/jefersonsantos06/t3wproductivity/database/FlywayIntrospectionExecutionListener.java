package com.github.jefersonsantos06.t3wproductivity.database;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Acompanha a saída de qualquer execução (Spring Boot, Application, testes...) e, quando o Flyway termina, pede o
 * refresh do banco migrado.
 */
public final class FlywayIntrospectionExecutionListener implements ExecutionListener {

    private final Project project;

    public FlywayIntrospectionExecutionListener(final Project project) {
        this.project = project;
    }

    @Override
    public void processStarted(@NotNull final String executorId, @NotNull final ExecutionEnvironment env,
                               @NotNull final ProcessHandler handler) {
        handler.addProcessListener(new FlywayOutputListener(project));
    }

    private static final class FlywayOutputListener implements ProcessListener {

        private static final int MAX_PENDING_CHARS = 64 * 1024;

        private final Project project;
        private final FlywayLogParser parser = new FlywayLogParser();
        private final StringBuilder pending = new StringBuilder();

        private FlywayOutputListener(final Project project) {
            this.project = project;
        }

        @Override
        public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final Key outputType) {
            if (ProcessOutputType.isStdout(outputType) || ProcessOutputType.isStderr(outputType)) {
                accept(event.getText());
            }
        }

        // A saída chega em pedaços; só linhas completas vão para o parser.
        private synchronized void accept(final String text) {
            pending.append(text);
            int start = 0;
            int end;
            while ((end = pending.indexOf("\n", start)) >= 0) {
                parser.accept(pending.substring(start, end))
                        .ifPresent(result -> IntrospectionRefreshService.getInstance().onFlywayFinished(project, result));
                start = end + 1;
            }
            pending.delete(0, start);
            // Saída sem quebra de linha (barras de progresso com \r) não pode crescer sem limite.
            if (pending.length() > MAX_PENDING_CHARS) {
                pending.setLength(0);
            }
        }
    }
}
