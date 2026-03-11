package com.github.jefersonsantos06.t3wproductivity.services;

import com.github.jefersonsantos06.t3wproductivity.MyBundle;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

@Service(Service.Level.PROJECT)
public final class MyProjectService {
    private static final Logger LOG = Logger.getInstance(MyProjectService.class);

    public MyProjectService(@NotNull Project project) {
        LOG.info(MyBundle.message("projectService", project.getName()));
        LOG.warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
    }

    public int getRandomNumber() {
        return ThreadLocalRandom.current().nextInt(1, 101);
    }
}
