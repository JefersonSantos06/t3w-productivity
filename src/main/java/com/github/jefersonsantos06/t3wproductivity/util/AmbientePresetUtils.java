package com.github.jefersonsantos06.t3wproductivity.util;

import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentPreset;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Gera presets a partir do enum {@code Ambiente} do projeto principal, lido pelo índice do IntelliJ.
 */
public final class AmbientePresetUtils {

    public static final List<String> AMBIENTE_FQNS = List.of("wmix.core.ambiente.Ambiente", "t3w.core.ambiente.Ambiente");
    public static final String AMBIENTE_OVERRIDE_KEY = "T3W_DEV_AMBIENTE_OVERRIDE";
    public static final String DB_NAME_KEY = "T3W_DEV_DB_NAME";

    private static final String PRESET_ID_PREFIX = "ambiente-";
    private static final Set<String> IGNORED_CODIGOS = Set.of("TDD", "DEV");

    private AmbientePresetUtils() {
    }

    public record AmbienteConstant(String codigo, @Nullable String descricao) {
    }

    public record MergeResult(List<EnvironmentPreset> presets, int created, int updated) {
    }

    /**
     * Lê as constantes do enum {@code Ambiente}. Deve ser chamado em read action e fora do dumb mode.
     * Prefere o fonte (worktree atual) ao jar; do jar só é possível ler o nome da constante.
     */
    public static Optional<List<AmbienteConstant>> readAmbientes(final Project project) {
        final var facade = JavaPsiFacade.getInstance(project);
        final var scope = GlobalSearchScope.allScope(project);
        for (final var fqn : AMBIENTE_FQNS) {
            final var ambienteClass = Arrays.stream(facade.findClasses(fqn, scope))
                    .map(psiClass -> psiClass.getNavigationElement() instanceof PsiClass source ? source : psiClass)
                    .filter(PsiClass::isEnum)
                    .min(Comparator.comparing(psiClass -> psiClass instanceof PsiCompiledElement));
            if (ambienteClass.isPresent()) {
                return Optional.of(readConstants(ambienteClass.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * Cria um preset por ambiente (exceto TDD e DEV) ou atualiza o já gerado, identificado por id fixo.
     * Na atualização só as chaves gerenciadas mudam: nome e variáveis extras do usuário são mantidos.
     * Presets existentes nunca são removidos.
     */
    public static MergeResult mergeAmbientePresets(final Collection<EnvironmentPreset> presets,
                                                   final Collection<AmbienteConstant> ambientes) {
        final var merged = new ArrayList<EnvironmentPreset>();
        presets.forEach(preset -> merged.add(preset.copy()));
        int created = 0;
        int updated = 0;

        for (final var ambiente : ambientes) {
            final var codigo = StringUtils.trimToEmpty(ambiente.codigo());
            if (codigo.isEmpty() || IGNORED_CODIGOS.contains(codigo.toUpperCase(Locale.ROOT))) {
                continue;
            }

            final var presetId = PRESET_ID_PREFIX + codigo;
            final var existing = merged.stream().filter(preset -> presetId.equals(preset.getId())).findFirst();
            if (existing.isPresent()) {
                final var variables = existing.get().getVariables();
                final var before = new LinkedHashMap<>(variables);
                variables.putAll(buildVariables(codigo));
                if (!before.equals(variables)) {
                    updated++;
                }
            } else {
                merged.add(new EnvironmentPreset(presetId, presetName(codigo, ambiente.descricao()), buildVariables(codigo)));
                created++;
            }
        }
        return new MergeResult(merged, created, updated);
    }

    private static Map<String, String> buildVariables(final String codigo) {
        final var variables = new LinkedHashMap<String, String>();
        variables.put(AMBIENTE_OVERRIDE_KEY, codigo);
        variables.put(DB_NAME_KEY, codigo.toLowerCase(Locale.ROOT));
        return variables;
    }

    private static String presetName(final String codigo, @Nullable final String descricao) {
        return StringUtils.isBlank(descricao) ? codigo : codigo + " - " + descricao.trim();
    }

    private static List<AmbienteConstant> readConstants(final PsiClass ambienteClass) {
        final var constants = new ArrayList<AmbienteConstant>();
        for (final var field : ambienteClass.getFields()) {
            if (!(field instanceof PsiEnumConstant constant)) {
                continue;
            }
            final var argumentList = constant.getArgumentList();
            final var arguments = argumentList == null ? PsiExpression.EMPTY_ARRAY : argumentList.getExpressions();
            constants.add(new AmbienteConstant(
                    stringArgument(arguments, 0).orElse(constant.getName()),
                    stringArgument(arguments, 1).orElse(null)
            ));
        }
        return constants;
    }

    private static Optional<String> stringArgument(final PsiExpression[] arguments, final int index) {
        if (index < arguments.length
            && arguments[index] instanceof PsiLiteralExpression literal
            && literal.getValue() instanceof String value) {
            return Optional.ofNullable(StringUtils.trimToNull(value));
        }
        return Optional.empty();
    }
}
