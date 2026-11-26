package com.github.jefersonsantos06.t3wproductivity.database;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Acompanha a saída de qualquer execução (Spring Boot, Application, testes...) e, quando o Flyway termina num banco
 * local, grava o evento no {@link FlywayEventLog}. Não depende do plugin Database: quem atualiza a introspecção é o
 * {@link FlywayEventWatcher} desta IDE ou de outra (ex.: DataGrip).
 */
public final class FlywayIntrospectionExecutionListener implements ExecutionListener {

    private static final Logger LOG = Logger.getInstance(FlywayIntrospectionExecutionListener.class);

    @Override
    public void processStarted(@NotNull final String executorId, @NotNull final ExecutionEnvironment env,
                               @NotNull final ProcessHandler handler) {
        handler.addProcessListener(new FlywayOutputListener());
    }

    private static void publish(final FlywayLogParser.Result result) {
        if (!result.location().isLocal()) {
            return;
        }
        try {
            FlywayEventLog.append(FlywayEventLog.SHARED, result);
        } catch (final IOException e) {
            LOG.warn("Não foi possível gravar o evento do Flyway em " + FlywayEventLog.SHARED, e);
        }
    }

    private static final class FlywayOutputListener implements ProcessListener {

        private static final int MAX_PENDING_CHARS = 64 * 1024;

        private final FlywayLogParser parser = new FlywayLogParser();
        private final StringBuilder pending = new StringBuilder();

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
                parser.accept(pending.substring(start, end)).ifPresent(FlywayIntrospectionExecutionListener::publish);
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
