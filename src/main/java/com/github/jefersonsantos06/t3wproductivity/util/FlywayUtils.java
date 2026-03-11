package com.github.jefersonsantos06.t3wproductivity.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class FlywayUtils {

    private static final Pattern FLYWAY_PATTERN = Pattern.compile("^V\\d{12}__(.+)$");
    public static final String FLYWAY_DEFAULT_PATH = "src/main/resources/db/migration";
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
        return "V" + LocalDateTime.now().format(DATE_FORMAT) + "__" + descriptionToken;
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

    public static Optional<String> tryUpdateDate(final String currentBaseName) {
        final var matcher = FLYWAY_PATTERN.matcher(currentBaseName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        final var description = matcher.group(1);
        if (StringUtils.trimToEmpty(description).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(buildVersionPrefix(description));
    }

    public static Optional<String> tryConvertToFlywayPattern(final String currentBaseName) {
        final var noPrefix = RegExUtils.replaceFirst(currentBaseName, "^V\\d+__", "");
        final var normalized = normalizeDescription(noPrefix);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(buildVersionPrefix(normalized));
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
