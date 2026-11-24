package com.github.jefersonsantos06.t3wproductivity.ambiente;

import com.github.jefersonsantos06.t3wproductivity.ambiente.AmbienteMatrix.Origin;
import com.github.jefersonsantos06.t3wproductivity.ambiente.AmbienteMatrix.Row;
import com.github.jefersonsantos06.t3wproductivity.util.AmbientePresetUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public class AmbienteMatrixBuilderTest extends LightJavaCodeInsightFixtureTestCase {

    private AmbienteMatrix matrix;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("""
                package wmix.core.ambiente;

                public enum Ambiente {
                    DEV("DEV", "Desenvolvimento"),
                    WMX("WMX", "Imagem Filmes"),
                    IFC("IFC", "Imagem Films Chile");

                    Ambiente(final String codigo, final String descricao) {
                    }

                    public String getDominio() {
                        return switch (this) {
                            case DEV -> "teste.com";
                            case WMX -> "wmixvideo.com.br";
                            default -> "imagemfilms.cl";
                        };
                    }

                    public boolean isUsaBoleto() {
                        switch (this) {
                            case WMX:
                            case DEV:
                                return true;
                            default:
                                return false;
                        }
                    }

                    public int getLimite(final String tipo) {
                        return switch (this) {
                            case WMX -> {
                                yield 10;
                            }
                            case IFC -> 20;
                            case DEV -> throw new IllegalStateException();
                        };
                    }

                    public String getSemSwitch() {
                        return "igual para todos";
                    }
                }
                """);
        matrix = AmbienteMatrixBuilder.build(AmbientePresetUtils.findAmbienteClass(getProject()).orElseThrow());
    }

    public void testListsAmbientesAndRowsWithSwitchOnThis() {
        assertEquals(List.of("DEV", "WMX", "IFC"), matrix.ambientes());
        assertEquals(List.of("codigo (construtor)", "descricao (construtor)", "getDominio()", "isUsaBoleto()", "getLimite(String)"),
                matrix.rows().stream().map(Row::name).toList());
    }

    public void testReadsExplicitCasesAndDefault() {
        assertCells("getDominio()", "\"teste.com\"", Origin.CASE, "\"wmixvideo.com.br\"", Origin.CASE, "\"imagemfilms.cl\"", Origin.DEFAULT);
        assertCells("isUsaBoleto()", "true", Origin.CASE, "true", Origin.CASE, "false", Origin.DEFAULT);
        assertCells("getLimite(String)", "throw new IllegalStateException();", Origin.CASE, "10", Origin.CASE, "20", Origin.CASE);
        assertCells("codigo (construtor)", "\"DEV\"", Origin.CONSTRUCTOR, "\"WMX\"", Origin.CONSTRUCTOR, "\"IFC\"", Origin.CONSTRUCTOR);
    }

    public void testFiltersOneAmbiente() {
        final var ifc = matrix.indexOf("IFC");

        assertEquals(5, matrix.filter(ifc, "", false).size());
        assertEquals(List.of("getDominio()", "isUsaBoleto()"),
                matrix.filter(ifc, "", true).stream().map(Row::name).toList());
        assertEquals(List.of("getDominio()"), matrix.filter(ifc, "imagemfilms", false).stream().map(Row::name).toList());
        assertEquals(List.of(), matrix.filter(matrix.indexOf("WMX"), "imagemfilms", false));
    }

    public void testFiltersAllAmbientes() {
        assertEquals(List.of("getDominio()", "isUsaBoleto()"),
                matrix.filter(AmbienteMatrix.ALL_AMBIENTES, "", true).stream().map(Row::name).toList());
        assertEquals(List.of("getLimite(String)"),
                matrix.filter(AmbienteMatrix.ALL_AMBIENTES, "limite", false).stream().map(Row::name).toList());
    }

    private void assertCells(final String rowName, final Object... expected) {
        final var row = matrix.rows().stream().filter(candidate -> candidate.name().equals(rowName)).findFirst().orElseThrow();
        for (int i = 0; i < row.cells().size(); i++) {
            assertEquals(rowName + "[" + i + "]", expected[i * 2], row.cells().get(i).value());
            assertEquals(rowName + "[" + i + "]", expected[i * 2 + 1], row.cells().get(i).origin());
        }
    }
}
