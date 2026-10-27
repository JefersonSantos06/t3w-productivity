package com.github.jefersonsantos06.t3wproductivity.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class SuggestEnvironmentVariableAction extends DumbAwareAction {

    public static final List<String> SUGGESTED_ENV_KEYS = List.of(
            "T3W_DEV_AMBIENTE_OVERRIDE",
            "T3W_DEV_DB_NAME",
            "T3W_DEV_DB_NAME_T3W_SITE",
            "T3W_DEV_DB_NAME_VAADIN_TOTEM",
            "T3W_DEV_DB_NAME_PAINEL",
            "T3W_DEV_DB_NAME_ORCAMENTO",
            "T3W_DEV_DB_NAME_CINEMA",
            "T3W_DEV_DB_NAME_EXIBIDOR",
            "T3W_DEV_DB_NAME_ADMIN",
            "T3W_DEV_DB_NAME_API",
            "T3W_DEV_DB_NAME_IMAGEM_SITE",
            "T3W_DEV_DB_NAME_DIREITOS",
            "T3W_DEV_DB_APPNAME_PREFIX"
    );

    private final JBTable table;
    private final EnvironmentTableModel model;

    public SuggestEnvironmentVariableAction(final JBTable table, final EnvironmentTableModel model) {
        super("Sugerir Variável", "Adicionar uma variável de ambiente sugerida", AllIcons.Actions.Lightning);
        this.table = table;
        this.model = model;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        final var suggestions = getAvailableSuggestions();
        if (suggestions.isEmpty()) {
            return;
        }

        final var selected = (String) JOptionPane.showInputDialog(
                table,
                "Selecione uma variável para adicionar:",
                "Sugerir variável de ambiente",
                JOptionPane.PLAIN_MESSAGE,
                null,
                suggestions.toArray(String[]::new),
                suggestions.getFirst()
        );
        if (selected == null) {
            return;
        }

        final var insertedRow = model.addRow(selected, "");
        table.getSelectionModel().setSelectionInterval(insertedRow, insertedRow);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
        e.getPresentation().setEnabled(!getAvailableSuggestions().isEmpty());
    }

    private List<String> getAvailableSuggestions() {
        final var currentKeys = model.getNormalizedKeys();
        final var available = new ArrayList<String>();
        for (final var suggestedKey : SUGGESTED_ENV_KEYS) {
            if (!currentKeys.contains(suggestedKey)) {
                available.add(suggestedKey);
            }
        }
        return available;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
