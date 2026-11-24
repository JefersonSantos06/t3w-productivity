package com.github.jefersonsantos06.t3wproductivity.migration;

import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.github.jefersonsantos06.t3wproductivity.util.FlywayUtils;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mantém em cache o que existe na branch base (via git, em segundo plano) e avalia os avisos das migrations.
 * Atualiza quando a pasta de migrations muda e, no máximo, a cada minuto quando uma migration é aberta.
 */
@Service(Service.Level.PROJECT)
public final class MigrationGuardService implements Disposable {

    private static final String NOTIFICATION_GROUP = "T3W Migrations";
    private static final long STALE_MILLIS = 60_000;
    private static final int EDIT_DEBOUNCE_MILLIS = 300;

    private record Location(Path repositoryRoot, String relativeDirectory, VirtualFile directory) {
    }

    private final Project project;
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicBoolean listening = new AtomicBoolean();
    private final Map<String, String> baseContents = new ConcurrentHashMap<>();
    private final Set<String> loadingContents = ConcurrentHashMap.newKeySet();
    private final Set<String> notifiedProblems = ConcurrentHashMap.newKeySet();
    private final Set<String> dismissedFiles = ConcurrentHashMap.newKeySet();
    private final Alarm editAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private volatile MigrationBaseSnapshot snapshot;
    private volatile long loadedAt;

    public MigrationGuardService(final Project project) {
        this.project = project;
    }

    public static MigrationGuardService getInstance(final Project project) {
        return project.getService(MigrationGuardService.class);
    }

    /**
     * Começa a acompanhar edições e mudanças na pasta de migrations e faz a primeira leitura da base.
     */
    public void start() {
        if (!listening.compareAndSet(false, true)) {
            return;
        }
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull final DocumentEvent event) {
                final var file = FileDocumentManager.getInstance().getFile(event.getDocument());
                if (file != null && isMigrationFile(file)) {
                    editAlarm.cancelAllRequests();
                    editAlarm.addRequest(() -> EditorNotifications.getInstance(project).updateNotifications(file), EDIT_DEBOUNCE_MILLIS);
                }
            }
        }, this);
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull final List<? extends @NotNull VFileEvent> events) {
                final var directory = migrationsDirectory().orElse(null);
                if (directory != null && events.stream().anyMatch(event -> isUnder(directory.getPath(), event.getPath()))) {
                    refresh();
                }
            }
        });
        refresh();
    }

    public boolean isMigrationFile(final VirtualFile file) {
        return !file.isDirectory()
               && FlywayUtils.extractVersion(file.getName()).isPresent()
               && migrationsDirectory().map(directory -> VfsUtilCore.isAncestor(directory, file, true)).orElse(false);
    }

    /**
     * Avisos para a migration aberta. Sem a base carregada (ou fora de um repositório git) não há avisos.
     */
    public List<MigrationGuardRules.Warning> warningsFor(final VirtualFile file, final String text) {
        if (dismissedFiles.contains(file.getPath())) {
            return List.of();
        }
        final var base = currentSnapshot();
        if (base == null) {
            return List.of();
        }
        final var baseText = base.fileNames().contains(file.getName()) ? baseContent(file).orElse(null) : null;
        return MigrationGuardRules.evaluate(file.getName(), text, baseText, base, localMigrationNames());
    }

    public Optional<String> baseContent(final VirtualFile file) {
        final var cached = baseContents.get(file.getName());
        if (cached != null) {
            return Optional.of(cached);
        }
        final var base = snapshot;
        final var location = location().orElse(null);
        if (base != null && location != null && loadingContents.add(file.getName())) {
            AppExecutorUtil.getAppExecutorService().execute(() -> {
                try {
                    MigrationBaseLoader.fileContent(location.repositoryRoot(), base.commit(), relativePath(location, file))
                            .ifPresent(content -> {
                                baseContents.put(file.getName(), content);
                                EditorNotifications.getInstance(project).updateNotifications(file);
                            });
                } finally {
                    loadingContents.remove(file.getName());
                }
            });
        }
        return Optional.empty();
    }

    public @Nullable String baseRef() {
        return Optional.ofNullable(snapshot).map(MigrationBaseSnapshot::baseRef).orElse(null);
    }

    public void dismiss(final VirtualFile file) {
        dismissedFiles.add(file.getPath());
        EditorNotifications.getInstance(project).updateNotifications(file);
    }

    private @Nullable MigrationBaseSnapshot currentSnapshot() {
        if (snapshot == null || System.currentTimeMillis() - loadedAt > STALE_MILLIS) {
            refresh();
        }
        return snapshot;
    }

    private void refresh() {
        if (project.isDisposed() || !loading.compareAndSet(false, true)) {
            return;
        }
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                final var loaded = location()
                        .flatMap(location -> MigrationBaseLoader.load(location.repositoryRoot(), location.relativeDirectory()))
                        .orElse(null);
                final var previous = snapshot;
                if (loaded == null || previous == null || !loaded.commit().equals(previous.commit())) {
                    baseContents.clear();
                }
                snapshot = loaded;
                loadedAt = System.currentTimeMillis();
                if (!project.isDisposed()) {
                    if (!Objects.equals(previous, loaded)) {
                        EditorNotifications.getInstance(project).updateAllNotifications();
                    }
                    notifyVersionProblems(loaded);
                }
            } finally {
                loading.set(false);
            }
        });
    }

    // Avisa uma vez por problema (ex.: depois de um merge) as migrations da branch fora de ordem ou com versão duplicada.
    private void notifyVersionProblems(@Nullable final MigrationBaseSnapshot base) {
        final var directory = migrationsDirectory().orElse(null);
        if (base == null || directory == null) {
            return;
        }
        final var localNames = localMigrationNames();
        for (final var file : migrationFiles(directory)) {
            if (base.fileNames().contains(file.getName())) {
                continue;
            }
            MigrationGuardRules.evaluate(file.getName(), "", null, base, localNames).stream()
                    .filter(warning -> warning.kind() == MigrationGuardRules.Kind.OUT_OF_ORDER
                                       || warning.kind() == MigrationGuardRules.Kind.DUPLICATE_VERSION)
                    .filter(warning -> notifiedProblems.add(file.getName() + "|" + warning.message()))
                    .forEach(warning -> NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                            .createNotification(file.getName(), warning.message(), NotificationType.WARNING)
                            .addAction(NotificationAction.createSimpleExpiring("Abrir migration",
                                    () -> new OpenFileDescriptor(project, file).navigate(true)))
                            .notify(project));
        }
    }

    private List<String> localMigrationNames() {
        return migrationsDirectory().map(directory -> migrationFiles(directory).stream().map(VirtualFile::getName).toList())
                .orElseGet(List::of);
    }

    private static List<VirtualFile> migrationFiles(final VirtualFile directory) {
        final var files = new ArrayList<VirtualFile>();
        VfsUtilCore.iterateChildrenRecursively(directory, null, file -> {
            if (!file.isDirectory() && FlywayUtils.extractVersion(file.getName()).isPresent()) {
                files.add(file);
            }
            return true;
        });
        return files;
    }

    // Sem refresh do VFS: é chamado em read action (avisos do editor) e em listeners.
    private Optional<VirtualFile> migrationsDirectory() {
        final var baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) {
            return Optional.empty();
        }
        final var configured = project.getService(FlywayProjectService.class).getMigrationsDirectory();
        return Optional.ofNullable(LocalFileSystem.getInstance()
                .findFileByPath(FlywayUtils.resolveDirectory(configured, baseDir.getPath())));
    }

    private Optional<Location> location() {
        return migrationsDirectory().flatMap(directory -> {
            final var directoryPath = Path.of(directory.getPath());
            return MigrationBaseLoader.findRepositoryRoot(directoryPath).map(root -> new Location(root,
                    root.relativize(directoryPath).toString().replace('\\', '/'), directory));
        });
    }

    private static String relativePath(final Location location, final VirtualFile file) {
        final var inDirectory = VfsUtilCore.getRelativePath(file, location.directory(), '/');
        return location.relativeDirectory().isEmpty() ? inDirectory : location.relativeDirectory() + "/" + inDirectory;
    }

    private static boolean isUnder(final String directoryPath, final String path) {
        return path.equals(directoryPath) || path.startsWith(directoryPath + "/");
    }

    @Override
    public void dispose() {
    }
}
