package com.github.jefersonsantos06.t3wproductivity.ui;

import com.github.jefersonsantos06.t3wproductivity.model.EnvironmentVariable;
import com.github.jefersonsantos06.t3wproductivity.util.EnvironmentVariablesUtil;
import org.apache.commons.lang3.StringUtils;

import javax.swing.table.AbstractTableModel;
import java.util.*;

public final class EnvironmentTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = { "Chave", "Valor" };
    private final List<EnvironmentVariable> rows = new ArrayList<>();

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(final int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
        final var row = rows.get(rowIndex);
        return columnIndex == 0 ? row.getKey() : row.getValue();
    }

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
        final var row = rows.get(rowIndex);
        final var value = aValue == null ? "" : aValue.toString();
        if (columnIndex == 0) {
            row.setKey(value);
        } else {
            row.setValue(value);
        }
        fireTableRowsUpdated(rowIndex, rowIndex);
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
        return true;
    }

    public void setRows(final Map<String, String> source) {
        rows.clear();
        rows.addAll(EnvironmentVariablesUtil.toEntries(source));
        fireTableDataChanged();
    }

    public Map<String, String> toMap() {
        return EnvironmentVariablesUtil.fromEntries(rows);
    }

    public void addRow() {
        addRow("", "");
    }

    public int addRow(final String key, final String value) {
        rows.add(new EnvironmentVariable(key, value));
        final var insertedRow = rows.size() - 1;
        fireTableRowsInserted(insertedRow, insertedRow);
        return insertedRow;
    }

    public void removeRow(final int row) {
        rows.remove(row);
        fireTableRowsDeleted(row, row);
    }

    public Set<String> getNormalizedKeys() {
        final var keys = new LinkedHashSet<String>();
        for (final var row : rows) {
            final var normalizedKey = StringUtils.trimToNull(row.getKey());
            if (normalizedKey != null) {
                keys.add(normalizedKey);
            }
        }
        return keys;
    }
}
