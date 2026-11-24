package com.github.jefersonsantos06.t3wproductivity.ambiente;

import com.github.jefersonsantos06.t3wproductivity.ambiente.AmbienteMatrix.Cell;
import com.github.jefersonsantos06.t3wproductivity.ambiente.AmbienteMatrix.Origin;
import com.github.jefersonsantos06.t3wproductivity.ambiente.AmbienteMatrix.Row;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Monta a {@link AmbienteMatrix} lendo o PSI do enum. Deve ser chamado em read action.
 */
public final class AmbienteMatrixBuilder {

    private static final int MAX_VALUE_LENGTH = 300;

    private AmbienteMatrixBuilder() {
    }

    public static AmbienteMatrix build(final PsiClass enumClass) {
        final var constants = Arrays.stream(enumClass.getFields())
                .filter(PsiEnumConstant.class::isInstance)
                .map(PsiEnumConstant.class::cast)
                .toList();
        final var names = constants.stream().map(PsiField::getName).toList();
        final var pointers = SmartPointerManager.getInstance(enumClass.getProject());

        final var rows = new ArrayList<>(constructorRows(enumClass, constants, pointers));
        for (final var method : enumClass.getMethods()) {
            if (!method.isConstructor() && method.getBody() != null) {
                findThisSwitch(method).ifPresent(switchBlock -> rows.add(switchRow(method, switchBlock, names, pointers)));
            }
        }
        return new AmbienteMatrix(enumClass.getName(), names, rows);
    }

    private static List<Row> constructorRows(final PsiClass enumClass, final List<PsiEnumConstant> constants,
                                             final SmartPointerManager pointers) {
        final var constructor = Arrays.stream(enumClass.getConstructors())
                .filter(candidate -> candidate.getParameterList().getParametersCount() > 0)
                .findFirst()
                .orElse(null);
        if (constructor == null) {
            return List.of();
        }
        final var parameters = constructor.getParameterList().getParameters();
        final var rows = new ArrayList<Row>();
        for (int index = 0; index < parameters.length; index++) {
            final var cells = new ArrayList<Cell>();
            for (final var constant : constants) {
                final var argumentList = constant.getArgumentList();
                final var arguments = argumentList == null ? PsiExpression.EMPTY_ARRAY : argumentList.getExpressions();
                cells.add(index < arguments.length
                        ? new Cell(text(arguments[index]), Origin.CONSTRUCTOR, pointers.createSmartPsiElementPointer(arguments[index]))
                        : new Cell("—", Origin.MISSING, pointers.createSmartPsiElementPointer(constant)));
            }
            rows.add(new Row(parameters[index].getName() + " (construtor)", cells,
                    pointers.createSmartPsiElementPointer(parameters[index])));
        }
        return rows;
    }

    private static Optional<PsiSwitchBlock> findThisSwitch(final PsiMethod method) {
        return PsiTreeUtil.findChildrenOfType(method.getBody(), PsiSwitchBlock.class).stream()
                .filter(switchBlock -> PsiUtil.skipParenthesizedExprDown(switchBlock.getExpression()) instanceof PsiThisExpression)
                .findFirst();
    }

    private static Row switchRow(final PsiMethod method, final PsiSwitchBlock switchBlock, final List<String> ambientes,
                                 final SmartPointerManager pointers) {
        final var explicit = new HashMap<String, Cell>();
        final Cell[] defaultCell = {null};
        final var body = switchBlock.getBody();
        final var pendingLabels = new ArrayList<PsiSwitchLabelStatement>();
        for (final var statement : body == null ? PsiStatement.EMPTY_ARRAY : body.getStatements()) {
            if (statement instanceof PsiSwitchLabeledRuleStatement rule) {
                final var cell = new Cell(ruleValue(rule.getBody()), Origin.CASE, pointers.createSmartPsiElementPointer(rule));
                register(rule, cell, explicit, defaultCell);
            } else if (statement instanceof PsiSwitchLabelStatement label) {
                pendingLabels.add(label);
            } else if (!pendingLabels.isEmpty()) {
                // "case X: return ...;" — o valor é o primeiro comando depois dos rótulos.
                final var cell = new Cell(statementValue(statement), Origin.CASE, pointers.createSmartPsiElementPointer(statement));
                pendingLabels.forEach(label -> register(label, cell, explicit, defaultCell));
                pendingLabels.clear();
            }
        }

        final var cells = ambientes.stream()
                .map(ambiente -> Optional.ofNullable(explicit.get(ambiente))
                        .or(() -> Optional.ofNullable(defaultCell[0]))
                        .orElseGet(() -> new Cell("—", Origin.MISSING, null)))
                .toList();
        return new Row(signature(method), cells, pointers.createSmartPsiElementPointer(method));
    }

    private static void register(final PsiSwitchLabelStatementBase label, final Cell cell, final Map<String, Cell> explicit,
                                 final Cell[] defaultCell) {
        if (label.isDefaultCase()) {
            defaultCell[0] = new Cell(cell.value(), Origin.DEFAULT, cell.anchor());
        }
        final var elements = label.getCaseLabelElementList();
        if (elements == null) {
            return;
        }
        for (final var element : elements.getElements()) {
            if (element instanceof PsiReferenceExpression reference && reference.getReferenceName() != null) {
                explicit.put(reference.getReferenceName(), cell);
            }
        }
    }

    private static String ruleValue(@Nullable final PsiStatement body) {
        if (body instanceof PsiExpressionStatement expression) {
            return text(expression.getExpression());
        }
        if (body instanceof PsiBlockStatement block) {
            final var statements = block.getCodeBlock().getStatements();
            return statements.length == 1 ? statementValue(statements[0]) : text(block);
        }
        return body == null ? "" : text(body);
    }

    private static String statementValue(final PsiStatement statement) {
        if (statement instanceof PsiReturnStatement returnStatement && returnStatement.getReturnValue() != null) {
            return text(returnStatement.getReturnValue());
        }
        if (statement instanceof PsiYieldStatement yieldStatement && yieldStatement.getExpression() != null) {
            return text(yieldStatement.getExpression());
        }
        if (statement instanceof PsiExpressionStatement expression) {
            return text(expression.getExpression());
        }
        return text(statement);
    }

    private static String signature(final PsiMethod method) {
        return method.getName() + Arrays.stream(method.getParameterList().getParameters())
                .map(parameter -> parameter.getType().getPresentableText())
                .collect(Collectors.joining(", ", "(", ")"));
    }

    private static String text(final PsiElement element) {
        final var collapsed = element.getText().replaceAll("\\s+", " ").strip();
        return collapsed.length() > MAX_VALUE_LENGTH ? collapsed.substring(0, MAX_VALUE_LENGTH) + "…" : collapsed;
    }
}
