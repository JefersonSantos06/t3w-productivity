package com.github.jefersonsantos06.t3wproductivity.database;

import com.intellij.database.actions.RefreshActionsLogic;
import com.intellij.database.dataSource.DataSourceStorage;
import com.intellij.database.dataSource.DataSourceSyncManager;
import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import com.intellij.database.model.basic.BasicNamespace;
import com.intellij.database.model.basic.BasicSchema;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Mantém a introspecção dos data sources PostgreSQL locais em dia sem ação manual: atualiza o banco depois que o Flyway
 * roda e introspecta os bancos do escopo que ainda não foram introspectados (ou perderam o cache).
 * <p>
 * Para não pesar na IDE: só data sources locais (localhost), só o banco envolvido, um refresh por vez (esperando a
 * sincronização que estiver em andamento) e nada quando o proxy/banco não está escutando.
 */
@Service(Service.Level.APP)
public final class IntrospectionRefreshService {

    private static final Logger LOG = Logger.getInstance(IntrospectionRefreshService.class);
    private static final String NOTIFICATION_GROUP = "T3W Database";
    private static final String MAIN_SCHEMA = "public";
    private static final int PORT_TIMEOUT_MILLIS = 500;
    private static final long POLL_MILLIS = 500;
    private static final long START_GRACE_MILLIS = 5_000;
    private static final long REFRESH_TIMEOUT_MINUTES = 15;

    private final ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("T3W Introspection", 1);
    // "dataSource|banco" -> versão do Flyway já refletida na introspecção nesta sessão da IDE
    private final Map<String, String> refreshedVersions = new ConcurrentHashMap<>();
    private final Set<String> checkedDataSources = ConcurrentHashMap.newKeySet();
    private final Set<String> queued = ConcurrentHashMap.newKeySet();

    public static IntrospectionRefreshService getInstance() {
        return ApplicationManager.getApplication().getService(IntrospectionRefreshService.class);
    }

    public void onFlywayFinished(final Project project, final FlywayLogParser.Result result) {
        final var databaseName = result.location().database();
        final var version = String.valueOf(result.version());
        for (final var dataSource : dataSources(project)) {
            final var sameServer = JdbcLocation.parse(dataSource.getUrl())
                    .map(location -> location.isSameLocalServer(result.location()))
                    .orElse(false);
            final var key = key(dataSource, databaseName);
            if (!sameServer || (result.appliedMigrations() == 0 && version.equals(refreshedVersions.get(key)))) {
                continue;
            }
            findDatabase(dataSource, databaseName).ifPresent(database -> enqueue(project, dataSource, database,
                    reason(result), () -> refreshedVersions.put(key, version)));
        }
    }

    public void introspectMissingDatabases(final Project project) {
        for (final var dataSource : dataSources(project)) {
            if (checkedDataSources.contains(dataSource.getUniqueId()) || isLoading(project, dataSource)) {
                continue;
            }
            final var databases = databases(dataSource);
            // Modelo sem nenhum banco: ainda não carregou ou nunca foi introspectado; não dispara nada às cegas.
            if (databases.isEmpty()) {
                continue;
            }
            checkedDataSources.add(dataSource.getUniqueId());
            databases.stream()
                    .filter(database -> isInScope(dataSource, database) && notIntrospected(dataSource, database))
                    .forEach(database -> enqueue(project, dataSource, database, "ainda não introspectado", () -> {
                    }));
        }
    }

    private void enqueue(final Project project, final LocalDataSource dataSource, final BasicNamespace database,
                         final String reason, final Runnable onRefreshed) {
        final var databaseName = database.getName();
        final var key = key(dataSource, databaseName);
        if (!queued.add(key)) {
            return;
        }
        LOG.info("Introspecção agendada: " + dataSource.getName() + "/" + databaseName + " (" + reason + ")");
        executor.execute(() -> {
            try {
                refresh(project, dataSource, database).ifPresent(elapsed -> {
                    final var confirmed = wasIntrospectedSince(dataSource, databaseName, Instant.now().minus(elapsed));
                    if (confirmed) {
                        onRefreshed.run();
                    }
                    notifyRefresh(project, dataSource, databaseName, reason, elapsed, confirmed);
                });
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final RuntimeException e) {
                LOG.warn("Falha na introspecção de " + key, e);
            } finally {
                queued.remove(key);
            }
        });
    }

    // Vazio = não executou (banco fora do ar ou projeto fechado).
    private static Optional<Duration> refresh(final Project project, final LocalDataSource dataSource,
                                              final BasicNamespace database) throws InterruptedException {
        final var port = JdbcLocation.parse(dataSource.getUrl()).map(JdbcLocation::port).orElse(-1);
        if (project.isDisposed() || !isListening(port)) {
            return Optional.empty();
        }
        final var syncManager = DataSourceSyncManager.Companion.getInstance();
        // Não empilha sobre uma sincronização em andamento (da própria IDE ou do usuário).
        waitWhileActive(syncManager, dataSource);

        final var start = System.nanoTime();
        // Mesmo refresh do botão "Refresh" para o nó do banco. A variante que devolve o resultado mudou de tipo entre
        // versões da IDE; esta é estável e o término é acompanhado pelo DataSourceSyncManager.
        ApplicationManager.getApplication().invokeAndWait(() -> {
            if (!project.isDisposed()) {
                RefreshActionsLogic.refreshSpecificFragments(project, List.of(database));
            }
        }, ModalityState.nonModal());

        final var startDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(START_GRACE_MILLIS);
        while (!syncManager.isActive(dataSource) && System.nanoTime() < startDeadline) {
            Thread.sleep(POLL_MILLIS);
        }
        waitWhileActive(syncManager, dataSource);
        return Optional.of(Duration.ofNanos(System.nanoTime() - start));
    }

    private static void waitWhileActive(final DataSourceSyncManager syncManager, final LocalDataSource dataSource)
            throws InterruptedException {
        final var deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(REFRESH_TIMEOUT_MINUTES);
        while (syncManager.isActive(dataSource) && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MILLIS);
        }
    }

    // Data sources PostgreSQL locais, globais e do projeto. Data sources remotos (produção) nunca são tocados.
    private static List<LocalDataSource> dataSources(final Project project) {
        final var byId = new LinkedHashMap<String, LocalDataSource>();
        for (final var storage : storages(project)) {
            for (final var dataSource : storage.getDataSources()) {
                final var local = JdbcLocation.parse(dataSource.getUrl()).map(JdbcLocation::isLocal).orElse(false);
                if (local && dataSource.getDbms().isPostgres()) {
                    byId.putIfAbsent(dataSource.getUniqueId(), dataSource);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static boolean isLoading(final Project project, final LocalDataSource dataSource) {
        return storages(project).stream().anyMatch(storage -> storage.isLoading(dataSource));
    }

    // Global e do projeto; qualquer um pode não existir (ex.: projeto ainda sem data sources).
    private static List<DataSourceStorage> storages(final Project project) {
        return Stream.of(DataSourceStorage.getStorage(), DataSourceStorage.getProjectStorage(project))
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<BasicNamespace> databases(final LocalDataSource dataSource) {
        final var result = new ArrayList<BasicNamespace>();
        final var model = dataSource.getModel();
        if (model == null) {
            return result;
        }
        for (final DasObject root : model.getModelRoots()) {
            if (root.getKind() == ObjectKind.DATABASE && root instanceof BasicNamespace database) {
                result.add(database);
            }
            for (final DasObject child : root.getDasChildren(ObjectKind.DATABASE)) {
                if (child instanceof BasicNamespace database) {
                    result.add(database);
                }
            }
        }
        return result;
    }

    private static Optional<BasicNamespace> findDatabase(final LocalDataSource dataSource, final String name) {
        return databases(dataSource).stream()
                .filter(database -> name.equals(database.getName()) && isInScope(dataSource, database))
                .findFirst();
    }

    private static boolean isInScope(final LocalDataSource dataSource, final BasicNamespace namespace) {
        return RefreshActionsLogic.contains(dataSource.getIntrospectionScope(), namespace);
    }

    // Banco que nunca listou schemas, ou cujo schema public (do escopo) não tem data de introspecção.
    private static boolean notIntrospected(final LocalDataSource dataSource, final BasicNamespace database) {
        final var schemas = database.getDasChildren(ObjectKind.SCHEMA).toList();
        if (schemas.isEmpty()) {
            return true;
        }
        return mainSchema(database)
                .filter(schema -> isInScope(dataSource, schema))
                .map(schema -> schema.getLastIntrospectionLocalTimestamp() == null)
                .orElse(false);
    }

    // O modelo é recriado na sincronização, então o banco é procurado de novo pelo nome.
    private static boolean wasIntrospectedSince(final LocalDataSource dataSource, final String databaseName, final Instant since) {
        return findDatabase(dataSource, databaseName)
                .flatMap(IntrospectionRefreshService::mainSchema)
                .map(BasicSchema::getLastIntrospectionLocalTimestamp)
                .map(timestamp -> !timestamp.isBefore(since.minusSeconds(1)))
                .orElse(false);
    }

    private static Optional<BasicSchema> mainSchema(final BasicNamespace database) {
        for (final DasObject schema : database.getDasChildren(ObjectKind.SCHEMA)) {
            if (MAIN_SCHEMA.equals(schema.getName()) && schema instanceof BasicSchema basic) {
                return Optional.of(basic);
            }
        }
        return Optional.empty();
    }

    private static boolean isListening(final int port) {
        if (port <= 0) {
            return false;
        }
        try (final var socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), PORT_TIMEOUT_MILLIS);
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    private static String key(final LocalDataSource dataSource, final String database) {
        return dataSource.getUniqueId() + "|" + database;
    }

    private static String reason(final FlywayLogParser.Result result) {
        return result.appliedMigrations() > 0
                ? "%d migration(s) aplicada(s)".formatted(result.appliedMigrations())
                : "Flyway na versão " + result.version();
    }

    private static void notifyRefresh(final Project project, final LocalDataSource dataSource, final String database,
                                      final String reason, final Duration elapsed, final boolean confirmed) {
        if (project.isDisposed()) {
            return;
        }
        final var content = "%s em \"%s\": %s (%.1f s)".formatted(database, dataSource.getName(), reason, elapsed.toMillis() / 1000.0);
        final var group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP);
        final var notification = confirmed
                ? group.createNotification("Introspecção atualizada", content, NotificationType.INFORMATION)
                : group.createNotification("Introspecção não confirmada", content + ". Verifique a janela Database.",
                NotificationType.WARNING);
        notification.notify(project);
    }
}
