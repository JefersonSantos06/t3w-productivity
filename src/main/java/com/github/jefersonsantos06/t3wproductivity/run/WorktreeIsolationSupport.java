package com.github.jefersonsantos06.t3wproductivity.run;

import com.github.jefersonsantos06.t3wproductivity.services.application.EnvironmentApplicationService;
import com.github.jefersonsantos06.t3wproductivity.util.WorktreeIsolationUtils;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Liga o {@link WorktreeIsolationUtils} a uma execução: descobre a worktree do projeto, o slot e o application.properties
 * do módulo da run configuration.
 */
public final class WorktreeIsolationSupport {

    private static final String APPLICATION_PROPERTIES = "src/main/resources/application.properties";

    record Isolation(WorktreeIsolationUtils.Worktree worktree, int slot, Map<String, String> variables) {

        String describe() {
            final var url = Optional.ofNullable(variables.get(WorktreeIsolationUtils.SERVER_PORT_KEY))
                    .map(port -> " -> http://localhost:" + port)
                    .orElse("");
            return "[T3W] Worktree %s (slot %d): %s%s%n".formatted(worktree.name(), slot, variables.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", ")), url);
        }
    }

    private WorktreeIsolationSupport() {
    }

    static Optional<Isolation> compute(final RunConfigurationBase<?> configuration, final Collection<String> definedKeys) {
        final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
        final var basePath = configuration.getProject().getBasePath();
        if (!appService.isWorktreeIsolationEnabled() || basePath == null) {
            return Optional.empty();
        }
        final var worktree = WorktreeIsolationUtils.detectWorktree(Path.of(basePath));
        if (!worktree.linked()) {
            return Optional.empty();
        }
        final var slot = appService.getWorktreeSlot(worktree.path());
        final var variables = WorktreeIsolationUtils.isolationVariables(applicationProperties(configuration), slot,
                worktree.name(), definedKeys);
        return variables.isEmpty() ? Optional.empty() : Optional.of(new Isolation(worktree, slot, variables));
    }

    /**
     * Worktree do projeto e seu slot (0 no checkout principal), para exibir na Tool Window.
     */
    public static String describeProject(final Project project) {
        final var appService = ApplicationManager.getApplication().getService(EnvironmentApplicationService.class);
        final var basePath = project.getBasePath();
        if (basePath == null) {
            return "";
        }
        final var worktree = WorktreeIsolationUtils.detectWorktree(Path.of(basePath));
        if (!appService.isWorktreeIsolationEnabled()) {
            return worktree.name() + ": isolamento desligado nas configurações";
        }
        if (!worktree.linked()) {
            return worktree.name() + ": checkout principal (portas e cookies originais)";
        }
        final var slot = appService.getWorktreeSlot(worktree.path());
        return "%s: slot %d (portas +%d, cookies de sessão com _%d)".formatted(worktree.name(), slot,
                slot * WorktreeIsolationUtils.PORT_STEP, slot);
    }

    private static Properties applicationProperties(final RunConfigurationBase<?> configuration) {
        final var properties = new Properties();
        if (!(configuration instanceof ApplicationConfiguration application)) {
            return properties;
        }
        final var module = application.getConfigurationModule().getModule();
        if (module == null) {
            return properties;
        }
        for (final var root : ModuleRootManager.getInstance(module).getContentRoots()) {
            final var file = root.findFileByRelativePath(APPLICATION_PROPERTIES);
            if (file != null) {
                try (final var reader = new StringReader(VfsUtilCore.loadText(file))) {
                    properties.load(reader);
                } catch (final IOException ignored) {
                    // Sem o arquivo legível o app sobe só com o prefixo do ApplicationName.
                }
                break;
            }
        }
        return properties;
    }
}
