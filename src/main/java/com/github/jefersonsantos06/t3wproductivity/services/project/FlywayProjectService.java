package com.github.jefersonsantos06.t3wproductivity.services.project;

import com.github.jefersonsantos06.t3wproductivity.util.FlywayUtils;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil.STORE_NAME_PREFX;
import static com.github.jefersonsantos06.t3wproductivity.util.FlywayUtils.FLYWAY_DEFAULT_PATH;

@Service(Service.Level.PROJECT)
@State(name = STORE_NAME_PREFX + "FlywaySettingsService", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class FlywayProjectService implements PersistentStateComponent<FlywayProjectService.State> {

    private State state = new State();

    public static final class State {
        public String migrationsDirectory = FLYWAY_DEFAULT_PATH;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getMigrationsDirectory() {
        return Optional.ofNullable(StringUtils.trimToNull(state.migrationsDirectory))
                .orElse(FLYWAY_DEFAULT_PATH);
    }

    public void setMigrationsDirectory(final String migrationsDirectory) {
        state.migrationsDirectory = StringUtils.trimToEmpty(migrationsDirectory);
    }

    public Optional<VirtualFile> getMigrationsDirectoryFile(final Project project) {
        final var baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) {
            return Optional.empty();
        }
        final var absolutePath = FlywayUtils.resolveDirectory(getMigrationsDirectory(), baseDir.getPath());
        return Optional.ofNullable(LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath));
    }

    /**
     * Migration de maior versão dentro da pasta configurada (incluindo subpastas).
     */
    public Optional<VirtualFile> findLatestMigrationFile(final Project project) {
        return getMigrationsDirectoryFile(project).flatMap(directory -> {
            final var filesByName = new HashMap<String, VirtualFile>();
            VfsUtilCore.iterateChildrenRecursively(directory, null, file -> {
                if (!file.isDirectory()) {
                    filesByName.putIfAbsent(file.getName(), file);
                }
                return true;
            });
            return FlywayUtils.findLatestMigration(filesByName.keySet()).map(filesByName::get);
        });
    }

    public VirtualFile createMigration(Project project, String migrationDescription) throws IOException {
        final var normalizedDescription = FlywayUtils.normalizeDescription(migrationDescription);
        if (normalizedDescription.isBlank()) {
            throw new IOException("O nome da migration não pode ficar vazio.");
        }

        final var baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) {
            throw new IOException("Não foi possível localizar a pasta do projeto.");
        }

        final var targetDirectory = FlywayUtils.resolveDirectory(getMigrationsDirectory(), baseDir.getPath());

        final var createdSqlFile = new VirtualFile[1];
        final var failure = new IOException[1];

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                final var migrationDir = VfsUtil.createDirectoryIfMissing(targetDirectory);
                if (migrationDir == null) {
                    throw new IOException("Não foi possível criar a pasta de migrations.");
                }

                final var versionDateTime = FlywayUtils.nextFreeVersionDateTime(LocalDateTime.now(), collectFileNames(migrationDir));
                final var sqlFileName = FlywayUtils.buildSqlFileName(FlywayUtils.buildVersionPrefix(normalizedDescription, versionDateTime));
                if (migrationDir.findChild(sqlFileName) != null) {
                    throw new IOException("Já existe um arquivo com o nome " + sqlFileName);
                }

                final var sqlFile = migrationDir.createChildData(this, sqlFileName);
                VfsUtil.saveText(sqlFile, "-- Migration Flyway\n-- Ajuste o SQL conforme necessário.\n");
                createdSqlFile[0] = sqlFile;
            } catch (IOException exception) {
                failure[0] = exception;
            }
        });

        if (failure[0] != null) {
            throw failure[0];
        }

        return createdSqlFile[0];
    }

    public void adjustMigrationName(final Project project, final VirtualFile file) throws IOException {
        if (!"sql".equalsIgnoreCase(file.getExtension())) {
            throw new IOException("Selecione um arquivo .sql para ajustar o nome.");
        }

        final var parent = file.getParent();
        if (parent == null) {
            throw new IOException("Não foi possível localizar a pasta do arquivo selecionado.");
        }

        final var description = FlywayUtils.extractMigrationDescription(file.getNameWithoutExtension());
        final var existingNames = collectFileNames(getMigrationsRoot(project, file));
        existingNames.remove(file.getName());
        final var versionDateTime = FlywayUtils.nextFreeVersionDateTime(LocalDateTime.now(), existingNames);
        final var suggestedFileName = FlywayUtils.buildSqlFileName(FlywayUtils.buildVersionPrefix(description, versionDateTime));

        final var selectedFileName = FlywayUtils.chooseFileName(project, suggestedFileName);
        if (selectedFileName.isEmpty()) {
            return;
        }

        final var finalFileName = selectedFileName.get();
        if (file.getName().equals(finalFileName)) {
            return;
        }

        final var existing = parent.findChild(finalFileName);
        if (existing != null && !existing.equals(file)) {
            throw new IOException("Já existe um arquivo com o nome " + finalFileName);
        }

        final var failure = new IOException[1];
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                file.rename(this, finalFileName);
            } catch (final IOException exception) {
                failure[0] = exception;
            }
        });

        if (failure[0] != null) {
            throw failure[0];
        }
    }

    /**
     * Pasta usada para checar versões já existentes: a pasta configurada, se contiver o arquivo, ou a pasta do arquivo.
     */
    private VirtualFile getMigrationsRoot(final Project project, final VirtualFile file) {
        return getMigrationsDirectoryFile(project)
                .filter(directory -> VfsUtilCore.isAncestor(directory, file, true))
                .orElse(file.getParent());
    }

    private static List<String> collectFileNames(final VirtualFile root) {
        final var names = new ArrayList<String>();
        VfsUtilCore.iterateChildrenRecursively(root, null, file -> {
            if (!file.isDirectory()) {
                names.add(file.getName());
            }
            return true;
        });
        return names;
    }

}
