package com.github.jefersonsantos06.t3wproductivity.database;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lê os eventos do Flyway gravados por qualquer IDE (esta ou o IntelliJ que roda o app, quando esta é o DataGrip) e pede
 * o refresh dos data sources locais em todos os projetos abertos. Custo por ciclo: um stat do arquivo.
 */
@Service(Service.Level.APP)
public final class FlywayEventWatcher implements Disposable {

    private static final Logger LOG = Logger.getInstance(FlywayEventWatcher.class);
    private static final long POLL_SECONDS = 2;

    private final FlywayEventLog log = new FlywayEventLog(FlywayEventLog.SHARED);
    private final ScheduledFuture<?> task = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(this::poll, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);

    public static void ensureStarted() {
        ApplicationManager.getApplication().getService(FlywayEventWatcher.class);
    }

    private void poll() {
        try {
            for (final var result : log.readNew()) {
                for (final var project : ProjectManager.getInstance().getOpenProjects()) {
                    if (!project.isDisposed()) {
                        IntrospectionRefreshService.getInstance().onFlywayFinished(project, result);
                    }
                }
            }
        } catch (final IOException e) {
            LOG.debug("Falha ao ler " + FlywayEventLog.SHARED, e);
        } catch (final RuntimeException e) {
            // Exceção não tratada cancelaria o agendamento.
            LOG.warn("Falha ao processar eventos do Flyway", e);
        }
    }

    @Override
    public void dispose() {
        task.cancel(false);
    }
}
