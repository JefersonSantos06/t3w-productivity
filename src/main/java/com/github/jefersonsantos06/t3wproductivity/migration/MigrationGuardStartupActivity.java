package com.github.jefersonsantos06.t3wproductivity.migration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

public final class MigrationGuardStartupActivity implements ProjectActivity {

    @Override
    public Object execute(@NotNull final Project project, @NotNull final Continuation<? super Unit> continuation) {
        MigrationGuardService.getInstance(project).start();
        return Unit.INSTANCE;
    }
}
