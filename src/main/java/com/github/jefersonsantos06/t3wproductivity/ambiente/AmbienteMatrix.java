package com.github.jefersonsantos06.t3wproductivity.ambiente;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Valor de cada ambiente para cada método do enum {@code Ambiente} que decide por {@code switch (this)}, além dos
 * argumentos do construtor.
 */
public record AmbienteMatrix(String className, List<String> ambientes, List<Row> rows) {

    public static final int ALL_AMBIENTES = -1;

    public enum Origin {
        CASE("case"), DEFAULT("default"), MISSING("não tratado"), CONSTRUCTOR("construtor");

        private final String label;

        Origin(final String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Cell(String value, Origin origin, @Nullable SmartPsiElementPointer<PsiElement> anchor) {
    }

    public record Row(String name, List<Cell> cells, @Nullable SmartPsiElementPointer<PsiElement> anchor) {
    }

    /**
     * Linhas que passam nos filtros.
     *
     * @param ambienteIndex índice em {@link #ambientes()} ou {@link #ALL_AMBIENTES}; os filtros olham só esse ambiente
     * @param search        trecho do nome do método ou do valor (sem diferenciar maiúsculas)
     * @param onlyDefault   só linhas em que o valor vem do {@code default}
     */
    public List<Row> filter(final int ambienteIndex, final String search, final boolean onlyDefault) {
        final var term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(row -> {
                    final var cells = ambienteIndex == ALL_AMBIENTES
                            ? row.cells()
                            : List.of(row.cells().get(ambienteIndex));
                    final var matchesSearch = term.isEmpty()
                                              || row.name().toLowerCase(Locale.ROOT).contains(term)
                                              || cells.stream().anyMatch(cell -> cell.value().toLowerCase(Locale.ROOT).contains(term));
                    final var matchesDefault = !onlyDefault || cells.stream().anyMatch(cell -> cell.origin() == Origin.DEFAULT);
                    return matchesSearch && matchesDefault;
                })
                .toList();
    }

    public int indexOf(final String ambiente) {
        return IntStream.range(0, ambientes.size())
                .filter(index -> ambientes.get(index).equals(ambiente))
                .findFirst()
                .orElse(ALL_AMBIENTES);
    }
}
