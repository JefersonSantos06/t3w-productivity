package com.github.jefersonsantos06.t3wproductivity.startup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public final class MyProjectActivity implements StartupActivity.DumbAware {
    private static final Logger LOG = Logger.getInstance(MyProjectActivity.class);

    @Override
    public void runActivity(@NotNull Project project) {
        LOG.warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
    }
}
