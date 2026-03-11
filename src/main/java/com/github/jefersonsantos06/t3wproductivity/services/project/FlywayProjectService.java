package com.github.jefersonsantos06.t3wproductivity.services.project;

import com.github.jefersonsantos06.t3wproductivity.util.FlywayUtils;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Optional;

import static com.github.jefersonsantos06.t3wproductivity.util.FlywayUtils.FLYWAY_DEFAULT_PATH;

@Service(Service.Level.PROJECT)
@State(name = "T3WFlywaySettingsService", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
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

    public VirtualFile createMigration(Project project, String migrationDescription) throws IOException {
        final var normalizedDescription = FlywayUtils.normalizeDescription(migrationDescription);
        if (normalizedDescription.isBlank()) {
            throw new IOException("O nome da migration nao pode ficar vazio.");
        }

        final var baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) {
            throw new IOException("Nao foi possivel localizar a pasta do projeto.");
        }

        final var settingsService = project.getService(FlywayProjectService.class);
        final var targetDirectory = FlywayUtils.resolveDirectory(settingsService.getMigrationsDirectory(), baseDir.getPath());
        final var versionPrefix = FlywayUtils.buildVersionPrefix(normalizedDescription);
        final var sqlFileName = FlywayUtils.buildSqlFileName(versionPrefix);

        final var createdSqlFile = new VirtualFile[1];
        final var failure = new IOException[1];

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                final var migrationDir = VfsUtil.createDirectoryIfMissing(targetDirectory);
                if (migrationDir == null) {
                    throw new IOException("Nao foi possivel criar a pasta de migrations.");
                }

                if (migrationDir.findChild(sqlFileName) != null) {
                    throw new IOException("Ja existe um arquivo com o nome " + sqlFileName);
                }

                final var sqlFile = migrationDir.createChildData(this, sqlFileName);
                VfsUtil.saveText(sqlFile, "-- Migration Flyway\n-- Ajuste o SQL conforme necessario.\n");
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

        final var currentBaseName = file.getNameWithoutExtension();
        final var suggestedBaseName = FlywayUtils.tryUpdateDate(currentBaseName)
                .or(() -> FlywayUtils.tryConvertToFlywayPattern(currentBaseName))
                .orElseGet(() -> FlywayUtils.buildVersionPrefix("migration"));
        final var suggestedFileName = FlywayUtils.buildSqlFileName(suggestedBaseName);

        final var selectedFileName = FlywayUtils.chooseFileName(project, suggestedFileName);
        if (selectedFileName.isEmpty()) {
            return;
        }

        final var finalFileName = selectedFileName.get();
        if (file.getName().equals(finalFileName)) {
            return;
        }

        final var parent = file.getParent();
        if (parent == null) {
            throw new IOException("Nao foi possivel localizar a pasta do arquivo selecionado.");
        }
        final var existing = parent.findChild(finalFileName);
        if (existing != null && !existing.equals(file)) {
            throw new IOException("Ja existe um arquivo com o nome " + finalFileName);
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

}
