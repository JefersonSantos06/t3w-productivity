package com.github.jefersonsantos06.t3wproductivity.actions.refactor.utils;

import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentPreset;
import com.github.jefersonsantos06.t3wproductivity.util.AmbientePresetUtils;
import com.github.jefersonsantos06.t3wproductivity.util.AmbientePresetUtils.AmbienteConstant;
import junit.framework.TestCase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AmbientePresetUtilsTest extends TestCase {

    private static final List<AmbienteConstant> AMBIENTES = List.of(
            new AmbienteConstant("TDD", "Testes unitários"),
            new AmbienteConstant("DEV", "Desenvolvimento"),
            new AmbienteConstant("WMX", "Imagem Filmes"),
            new AmbienteConstant("T3W", "T3w Tecnologia")
    );

    public void testCreatesPresetPerAmbienteExceptTddAndDev() {
        final var result = AmbientePresetUtils.mergeAmbientePresets(List.of(), AMBIENTES);

        assertEquals(2, result.created());
        assertEquals(0, result.updated());
        assertEquals(List.of("ambiente-WMX", "ambiente-T3W"), result.presets().stream().map(EnvironmentPreset::getId).toList());

        final var wmx = result.presets().getFirst();
        assertEquals("WMX - Imagem Filmes", wmx.getName());
        assertEquals(Map.of("T3W_DEV_AMBIENTE_OVERRIDE", "WMX", "T3W_DEV_DB_NAME", "wmx"), wmx.getVariables());
    }

    public void testUsesCodigoAsNameWhenDescricaoIsMissing() {
        final var result = AmbientePresetUtils.mergeAmbientePresets(List.of(), List.of(new AmbienteConstant("IFC", null)));
        assertEquals("IFC", result.presets().getFirst().getName());
    }

    public void testRegenerationUpdatesManagedKeysAndKeepsUserChanges() {
        final var variables = new LinkedHashMap<String, String>();
        variables.put("T3W_DEV_AMBIENTE_OVERRIDE", "XXX");
        variables.put("T3W_DEV_DB_NAME", "outro_banco");
        variables.put("T3W_DEV_DB_APPNAME_PREFIX", "meu-");
        final var renamed = new EnvironmentPreset("ambiente-WMX", "Imagem (meu nome)", variables);
        final var manual = EnvironmentPreset.create("Banco A", Map.of("T3W_DEV_DB_NAME", "a"));

        final var result = AmbientePresetUtils.mergeAmbientePresets(List.of(manual, renamed), AMBIENTES);

        assertEquals(1, result.created());
        assertEquals(1, result.updated());
        assertEquals(3, result.presets().size());
        assertEquals(manual, result.presets().getFirst());

        final var wmx = result.presets().get(1);
        assertEquals("Imagem (meu nome)", wmx.getName());
        assertEquals("WMX", wmx.getVariables().get("T3W_DEV_AMBIENTE_OVERRIDE"));
        assertEquals("wmx", wmx.getVariables().get("T3W_DEV_DB_NAME"));
        assertEquals("meu-", wmx.getVariables().get("T3W_DEV_DB_APPNAME_PREFIX"));
        assertEquals("XXX", renamed.getVariables().get("T3W_DEV_AMBIENTE_OVERRIDE"));
    }

    public void testRegenerationWithoutChangesCountsNothing() {
        final var first = AmbientePresetUtils.mergeAmbientePresets(List.of(), AMBIENTES);
        final var second = AmbientePresetUtils.mergeAmbientePresets(first.presets(), AMBIENTES);

        assertEquals(0, second.created());
        assertEquals(0, second.updated());
        assertEquals(first.presets(), second.presets());
    }

    public void testKeepsPresetsOfAmbientesMissingInCurrentBranch() {
        final var ifo = new EnvironmentPreset("ambiente-IFO", "IFO - Imagem Films Colombia", Map.of("T3W_DEV_DB_NAME", "ifo"));

        final var result = AmbientePresetUtils.mergeAmbientePresets(List.of(ifo), AMBIENTES);

        assertTrue(result.presets().contains(ifo));
    }
}
