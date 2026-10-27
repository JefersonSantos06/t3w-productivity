package com.github.jefersonsantos06.t3wproductivity.actions.refactor.utils;

import com.github.jefersonsantos06.t3wproductivity.util.AmbientePresetUtils;
import com.github.jefersonsantos06.t3wproductivity.util.AmbientePresetUtils.AmbienteConstant;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;
import java.util.Optional;

public class AmbientePresetPsiTest extends LightJavaCodeInsightFixtureTestCase {

    public void testReadsEnumConstantsFromSource() {
        myFixture.addClass("""
                package wmix.core.ambiente;

                public enum Ambiente {
                    TDD("TDD", "Testes unitários"),
                    WMX("WMX", "Imagem Filmes"),
                    IFC("IFC", "Imagem Films Chile");

                    public static final String OUTRA_CONSTANTE = "x";

                    Ambiente(final String codigo, final String descricao) {
                    }
                }
                """);

        assertEquals(
                Optional.of(List.of(
                        new AmbienteConstant("TDD", "Testes unitários"),
                        new AmbienteConstant("WMX", "Imagem Filmes"),
                        new AmbienteConstant("IFC", "Imagem Films Chile")
                )),
                AmbientePresetUtils.readAmbientes(getProject())
        );
    }

    public void testFallsBackToT3wPackage() {
        myFixture.addClass("""
                package t3w.core.ambiente;

                public enum Ambiente {
                    T3W("T3W", "T3w Tecnologia");

                    Ambiente(final String codigo, final String descricao) {
                    }
                }
                """);

        assertEquals(
                Optional.of(List.of(new AmbienteConstant("T3W", "T3w Tecnologia"))),
                AmbientePresetUtils.readAmbientes(getProject())
        );
    }

    public void testReturnsEmptyWhenEnumIsMissing() {
        assertEquals(Optional.empty(), AmbientePresetUtils.readAmbientes(getProject()));
    }
}
