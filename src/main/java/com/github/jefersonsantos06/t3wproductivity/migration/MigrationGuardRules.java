package com.github.jefersonsantos06.t3wproductivity.migration;

import com.github.jefersonsantos06.t3wproductivity.util.FlywayUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Avisos sobre uma migration. Só avisam: nada impede salvar, commitar ou rodar.
 */
public final class MigrationGuardRules {

    public static final String FUNCTION_PREFIX = "t3w_";

    public enum Kind {
        MERGED_EDITED, OUT_OF_ORDER, DUPLICATE_VERSION, FUNCTION_WITHOUT_PREFIX
    }

    public record Warning(Kind kind, String message) {
    }

    private static final Pattern CREATE_FUNCTION = Pattern.compile(
            "(?i)\\bcreate\\s+(?:or\\s+replace\\s+)?function\\s+((?:\"?[\\w$]+\"?\\.)?\"?[\\w$]+\"?)");
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");

    private MigrationGuardRules() {
    }

    /**
     * @param baseText conteúdo do arquivo na base, quando ele existe lá e já foi carregado
     */
    public static List<Warning> evaluate(final String fileName, final String text, @Nullable final String baseText,
                                         final MigrationBaseSnapshot base, final Collection<String> localFileNames) {
        final var warnings = new ArrayList<Warning>();
        final var inBase = base.fileNames().contains(fileName);

        if (inBase && baseText != null && !normalize(text).equals(normalize(baseText))) {
            warnings.add(new Warning(Kind.MERGED_EDITED, ("Esta migration já está no %s: alterações aqui não chegam aos bancos "
                                                          + "que já a executaram. Crie uma nova migration.").formatted(base.baseRef())));
        }

        final var version = FlywayUtils.extractVersion(fileName).orElse(null);
        if (version == null) {
            return warnings;
        }
        if (!inBase) {
            base.latestFileName()
                    .filter(latest -> FlywayUtils.compareVersions(version, FlywayUtils.extractVersion(latest).orElseThrow()) < 0)
                    .ifPresent(latest -> warnings.add(new Warning(Kind.OUT_OF_ORDER, ("Versão anterior à última migration do %s (%s): "
                                                                                      + "nos bancos já atualizados o Flyway vai ignorá-la. Renumere para agora.")
                            .formatted(base.baseRef(), latest))));
        }
        Stream.concat(base.fileNames().stream(), localFileNames.stream())
                .filter(other -> !other.equals(fileName))
                .filter(other -> FlywayUtils.extractVersion(other)
                        .map(otherVersion -> FlywayUtils.compareVersions(version, otherVersion) == 0)
                        .orElse(false))
                .findFirst()
                .ifPresent(other -> warnings.add(new Warning(Kind.DUPLICATE_VERSION,
                        "Mesma versão de %s: o Flyway falha ao iniciar. Renumere para agora.".formatted(other))));

        if (!inBase) {
            final var unprefixed = createdFunctions(text).stream()
                    .filter(name -> !name.startsWith(FUNCTION_PREFIX) && !base.functionNames().contains(name))
                    .toList();
            if (!unprefixed.isEmpty()) {
                warnings.add(new Warning(Kind.FUNCTION_WITHOUT_PREFIX,
                        "Função nova sem o prefixo %s: %s.".formatted(FUNCTION_PREFIX, String.join(", ", unprefixed))));
            }
        }
        return warnings;
    }

    /**
     * Funções criadas no SQL, em minúsculas, sem schema e sem aspas (comentários são ignorados).
     */
    public static Set<String> createdFunctions(final String sql) {
        final var withoutComments = LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(sql).replaceAll(" ")).replaceAll(" ");
        final var names = new LinkedHashSet<String>();
        final var matcher = CREATE_FUNCTION.matcher(withoutComments);
        while (matcher.find()) {
            final var qualified = matcher.group(1).replace("\"", "");
            names.add(qualified.substring(qualified.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static String normalize(final String text) {
        return text.replace("\r\n", "\n").strip();
    }
}
