package com.github.jefersonsantos06.t3wproductivity.database;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.concurrency.AppExecutorUtil;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Depois que o projeto abre e a indexação termina, procura bancos do escopo sem introspecção. Espera um pouco para não
 * competir com o startup e tenta de novo mais tarde para os data sources que ainda estavam carregando o cache.
 */
public final class MissingIntrospectionStartupActivity implements ProjectActivity {

    private static final List<Long> CHECK_DELAYS_SECONDS = List.of(60L, 300L);

    @Override
    public Object execute(@NotNull final Project project, @NotNull final Continuation<? super Unit> continuation) {
        for (final var delay : CHECK_DELAYS_SECONDS) {
            AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
                if (!project.isDisposed()) {
                    DumbService.getInstance(project).runWhenSmart(() -> AppExecutorUtil.getAppExecutorService().execute(() -> {
                        if (!project.isDisposed()) {
                            IntrospectionRefreshService.getInstance().introspectMissingDatabases(project);
                        }
                    }));
                }
            }, delay, TimeUnit.SECONDS);
        }
        return Unit.INSTANCE;
    }
}
