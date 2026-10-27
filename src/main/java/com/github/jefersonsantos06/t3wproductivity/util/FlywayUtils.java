package com.github.jefersonsantos06.t3wproductivity.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class FlywayUtils {

    private static final Pattern FLYWAY_PATTERN = Pattern.compile("^V\\d{12}__(.+)$");
    private static final Pattern MIGRATION_FILE_PATTERN = Pattern.compile("^V(\\d+(?:[._]\\d+)*)__.+\\.(?i:sql|java)$");
    public static final String FLYWAY_DEFAULT_PATH = "jbear-vaadin-admin/src/main/java/db/migration";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private FlywayUtils() {
    }

    public static String normalizeDescription(final String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9]+", "_");
        return value.replaceAll("^_+|_+$", "");
    }

    public static String buildVersionPrefix(final String descriptionToken) {
        return buildVersionPrefix(descriptionToken, LocalDateTime.now());
    }

    public static String buildVersionPrefix(final String descriptionToken, final LocalDateTime dateTime) {
        return "V" + dateTime.format(DATE_FORMAT) + "__" + descriptionToken;
    }

    public static String buildSqlFileName(final String versionPrefix) {
        return versionPrefix + ".sql";
    }

    public static Optional<String> chooseFileName(final Project project, final String suggestedFileName) {
        final var response = Messages.showDialog(
                project,
                "Nome sugerido: " + suggestedFileName + "\n\nEscolha como deseja prosseguir.",
                "Ajustar Nome Da Migration",
                new String[]{ "Atualizar Automaticamente", "Editar Nome", "Cancelar" },
                0,
                Messages.getQuestionIcon()
        );

        if (response == 2 || response < 0) {
            return Optional.empty();
        }

        if (response == 0) {
            return Optional.of(suggestedFileName);
        }

        final var customName = Messages.showInputDialog(
                project,
                "Informe o nome final do arquivo (.sql):",
                "Editar Nome Da Migration",
                Messages.getQuestionIcon(),
                suggestedFileName,
                null
        );

        if (customName == null) {
            return Optional.empty();
        }

        final var trimmed = StringUtils.trimToEmpty(customName);
        if (trimmed.isBlank()) {
            return Optional.empty();
        }

        if (trimmed.toLowerCase().endsWith(".sql")) {
            return Optional.of(trimmed);
        }
        return Optional.of(trimmed + ".sql");
    }

    /**
     * Descrição a reaproveitar ao renomear: mantém a de um nome já no padrão ou normaliza o nome atual.
     */
    public static String extractMigrationDescription(final String currentBaseName) {
        final var matcher = FLYWAY_PATTERN.matcher(currentBaseName);
        if (matcher.matches() && StringUtils.isNotBlank(matcher.group(1))) {
            return matcher.group(1);
        }
        final var normalized = normalizeDescription(RegExUtils.replaceFirst(currentBaseName, "^V\\d+__", ""));
        return normalized.isBlank() ? "migration" : normalized;
    }

    /**
     * Versão Flyway de um arquivo de migration versionada ({@code V<versão>__<descrição>.sql|java}).
     */
    public static Optional<String> extractVersion(final String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        final var matcher = MIGRATION_FILE_PATTERN.matcher(fileName);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * Compara versões Flyway numericamente por partes (separadas por "." ou "_").
     */
    public static int compareVersions(final String left, final String right) {
        final var leftParts = left.split("[._]");
        final var rightParts = right.split("[._]");
        for (int i = 0; i < Math.max(leftParts.length, rightParts.length); i++) {
            final var leftPart = i < leftParts.length ? new BigInteger(leftParts[i]) : BigInteger.ZERO;
            final var rightPart = i < rightParts.length ? new BigInteger(rightParts[i]) : BigInteger.ZERO;
            final var comparison = leftPart.compareTo(rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    public static Optional<String> findLatestMigration(final Collection<String> fileNames) {
        final Comparator<String> byVersion = Comparator.comparing(name -> extractVersion(name).orElseThrow(), FlywayUtils::compareVersions);
        return fileNames.stream()
                .filter(name -> extractVersion(name).isPresent())
                .max(byVersion.thenComparing(Comparator.naturalOrder()));
    }

    /**
     * Primeiro minuto a partir de {@code start} cuja versão ainda não é usada por nenhuma migration existente,
     * evitando versões duplicadas quando mais de uma migration é criada no mesmo minuto.
     */
    public static LocalDateTime nextFreeVersionDateTime(final LocalDateTime start, final Collection<String> existingFileNames) {
        final var usedVersions = existingFileNames.stream()
                .map(FlywayUtils::extractVersion)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        var candidate = start.truncatedTo(ChronoUnit.MINUTES);
        while (usedVersions.contains(candidate.format(DATE_FORMAT))) {
            candidate = candidate.plusMinutes(1);
        }
        return candidate;
    }

    public static String resolveDirectory(final String configuredDir, final String projectBasePath) {
        final var value = StringUtils.trimToEmpty(configuredDir);
        if (value.isBlank()) {
            return (projectBasePath + "/" + FLYWAY_DEFAULT_PATH).replace('\\', '/');
        }
        if (value.matches("^[A-Za-z]:[\\\\/].*") || value.startsWith("/") || value.startsWith("\\")) {
            return value.replace('\\', '/');
        }
        return (projectBasePath + "/" + value).replace('\\', '/');
    }
}
