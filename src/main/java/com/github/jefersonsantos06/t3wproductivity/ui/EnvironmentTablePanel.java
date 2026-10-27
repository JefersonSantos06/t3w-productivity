package com.github.jefersonsantos06.t3wproductivity.ui;

import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import java.awt.*;

/**
 * Tabela chave/valor editável com toolbar (adicionar, remover, sugerir variável).
 */
public final class EnvironmentTablePanel {

    private EnvironmentTablePanel() {
    }

    public static JPanel create(final String title, final EnvironmentTableModel model) {
        final var table = new JBTable(model);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        final var decorated = ToolbarDecorator.createDecorator(table)
                .setAddAction(button -> {
                    TableUtil.stopEditing(table);
                    model.addRow();
                })
                .setRemoveAction(button -> {
                    TableUtil.stopEditing(table);
                    final var selected = table.getSelectedRow();
                    if (selected >= 0) {
                        model.removeRow(selected);
                    }
                })
                .disableUpDownActions()
                .addExtraAction(new SuggestEnvironmentVariableAction(table, model))
                .createPanel();

        final var section = new JPanel(new BorderLayout(0, 8));
        section.add(new JLabel(title), BorderLayout.NORTH);
        section.add(decorated, BorderLayout.CENTER);
        return section;
    }
}
