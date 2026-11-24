package com.github.jefersonsantos06.t3wproductivity.ambiente;

import com.github.jefersonsantos06.t3wproductivity.ambiente.AmbienteMatrix.Cell;
import com.github.jefersonsantos06.t3wproductivity.ambiente.AmbienteMatrix.Origin;
import com.github.jefersonsantos06.t3wproductivity.ambiente.AmbienteMatrix.Row;
import com.github.jefersonsantos06.t3wproductivity.util.AmbientePresetUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;

/**
 * Tabela método x ambiente do enum {@code Ambiente}. Com um ambiente escolhido, mostra todos os valores só dele.
 */
public final class AmbienteMatrixPanel {

    private static final String ALL = "Todos os ambientes";
    private static final int NAME_WIDTH = 260;
    private static final int VALUE_WIDTH = 200;

    private final Project project;
    private final Disposable disposable;
    private final JPanel component = new JPanel(new BorderLayout(0, JBUI.scale(8)));
    private final DefaultComboBoxModel<String> ambienteModel = new DefaultComboBoxModel<>(new String[]{ ALL });
    private final SearchTextField searchField = new SearchTextField(false);
    private final JBCheckBox onlyDefaultCheckBox = new JBCheckBox("Só valores vindos do default");
    private final JBLabel statusLabel = new JBLabel();
    private final MatrixTableModel tableModel = new MatrixTableModel();
    private final JBTable table = new JBTable(tableModel);
    private AmbienteMatrix matrix;

    public AmbienteMatrixPanel(final Project project, final Disposable disposable) {
        this.project = project;
        this.disposable = disposable;

        final var ambienteCombo = new ComboBox<>(ambienteModel);
        ambienteCombo.addActionListener(event -> applyFilter());
        onlyDefaultCheckBox.addActionListener(event -> applyFilter());
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull final DocumentEvent event) {
                applyFilter();
            }
        });
        final var refreshButton = new JButton("Atualizar");
        refreshButton.addActionListener(event -> reload());

        final var filters = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        filters.add(new JBLabel("Ambiente:"));
        filters.add(ambienteCombo);
        filters.add(searchField);
        filters.add(onlyDefaultCheckBox);
        filters.add(refreshButton);

        table.setDefaultRenderer(Object.class, new MatrixCellRenderer());
        table.getTableHeader().setReorderingAllowed(false);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent event) {
                if (event.getClickCount() == 2) {
                    navigate(table.rowAtPoint(event.getPoint()), table.columnAtPoint(event.getPoint()));
                }
            }
        });
        statusLabel.setForeground(UIUtil.getContextHelpForeground());

        component.add(filters, BorderLayout.NORTH);
        component.add(new JBScrollPane(table), BorderLayout.CENTER);
        component.add(statusLabel, BorderLayout.SOUTH);
        component.setBorder(JBUI.Borders.empty(12));
    }

    public JComponent getComponent() {
        return component;
    }

    /**
     * Relê o enum em segundo plano (depois da indexação) e mantém o ambiente e os filtros escolhidos.
     */
    public void reload() {
        statusLabel.setText("Lendo o enum Ambiente...");
        ReadAction.nonBlocking(() -> AmbientePresetUtils.findAmbienteClass(project).map(AmbienteMatrixBuilder::build).orElse(null))
                .inSmartMode(project)
                .expireWith(disposable)
                .finishOnUiThread(ModalityState.any(), this::show)
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private void show(final AmbienteMatrix loaded) {
        matrix = loaded;
        final var selected = (String) ambienteModel.getSelectedItem();
        ambienteModel.removeAllElements();
        ambienteModel.addElement(ALL);
        Optional.ofNullable(loaded).map(AmbienteMatrix::ambientes).orElseGet(List::of).forEach(ambienteModel::addElement);
        ambienteModel.setSelectedItem(loaded != null && loaded.ambientes().contains(selected) ? selected : ALL);
        applyFilter();
    }

    private void applyFilter() {
        if (matrix == null) {
            tableModel.update(null, AmbienteMatrix.ALL_AMBIENTES, List.of());
            statusLabel.setText("Enum Ambiente não encontrado neste projeto.");
            return;
        }
        final var ambienteIndex = matrix.indexOf((String) ambienteModel.getSelectedItem());
        final var rows = matrix.filter(ambienteIndex, searchField.getText(), onlyDefaultCheckBox.isSelected());
        tableModel.update(matrix, ambienteIndex, rows);
        configureColumns(ambienteIndex);
        statusLabel.setText("%d de %d linhas · %d ambientes · duplo clique abre o código".formatted(rows.size(),
                matrix.rows().size(), matrix.ambientes().size()));
    }

    private void configureColumns(final int ambienteIndex) {
        final var columns = table.getColumnModel();
        if (ambienteIndex == AmbienteMatrix.ALL_AMBIENTES) {
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            for (int column = 0; column < columns.getColumnCount(); column++) {
                columns.getColumn(column).setPreferredWidth(JBUI.scale(column == 0 ? NAME_WIDTH : VALUE_WIDTH));
            }
        } else {
            table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
            columns.getColumn(0).setPreferredWidth(JBUI.scale(NAME_WIDTH));
            columns.getColumn(1).setPreferredWidth(JBUI.scale(VALUE_WIDTH * 3));
            columns.getColumn(2).setPreferredWidth(JBUI.scale(90));
        }
    }

    private void navigate(final int viewRow, final int column) {
        if (viewRow < 0) {
            return;
        }
        final var row = tableModel.rowAt(viewRow);
        final var cell = column > 0 ? tableModel.cellAt(viewRow, column) : null;
        Optional.ofNullable(cell).map(Cell::anchor)
                .or(() -> Optional.ofNullable(row.anchor()))
                .map(SmartPsiElementPointer::getElement)
                .ifPresent(element -> PsiNavigateUtil.navigate((PsiElement) element));
    }

    private static final class MatrixTableModel extends AbstractTableModel {

        private AmbienteMatrix matrix;
        private int ambienteIndex = AmbienteMatrix.ALL_AMBIENTES;
        private List<Row> rows = List.of();

        void update(final AmbienteMatrix matrix, final int ambienteIndex, final List<Row> rows) {
            final var structureChanged = this.matrix != matrix || this.ambienteIndex != ambienteIndex;
            this.matrix = matrix;
            this.ambienteIndex = ambienteIndex;
            this.rows = rows;
            if (structureChanged) {
                fireTableStructureChanged();
            } else {
                fireTableDataChanged();
            }
        }

        Row rowAt(final int row) {
            return rows.get(row);
        }

        Cell cellAt(final int row, final int column) {
            return ambienteIndex == AmbienteMatrix.ALL_AMBIENTES
                    ? rows.get(row).cells().get(column - 1)
                    : rows.get(row).cells().get(ambienteIndex);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            if (matrix == null) {
                return 1;
            }
            return ambienteIndex == AmbienteMatrix.ALL_AMBIENTES ? 1 + matrix.ambientes().size() : 3;
        }

        @Override
        public String getColumnName(final int column) {
            if (column == 0) {
                return "Método";
            }
            if (ambienteIndex == AmbienteMatrix.ALL_AMBIENTES) {
                return matrix.ambientes().get(column - 1);
            }
            return column == 1 ? matrix.ambientes().get(ambienteIndex) : "Origem";
        }

        @Override
        public Object getValueAt(final int row, final int column) {
            if (column == 0) {
                return rows.get(row).name();
            }
            final var cell = cellAt(row, column);
            return ambienteIndex != AmbienteMatrix.ALL_AMBIENTES && column == 2 ? cell.origin().label() : cell;
        }
    }

    private static final class MatrixCellRenderer extends ColoredTableCellRenderer {

        @Override
        protected void customizeCellRenderer(@NotNull final JTable table, final Object value, final boolean selected,
                                             final boolean hasFocus, final int row, final int column) {
            if (!(value instanceof Cell cell)) {
                append(String.valueOf(value));
                setToolTipText(null);
                return;
            }
            final var attributes = switch (cell.origin()) {
                case DEFAULT -> SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES;
                case MISSING -> SimpleTextAttributes.ERROR_ATTRIBUTES;
                case CASE, CONSTRUCTOR -> SimpleTextAttributes.REGULAR_ATTRIBUTES;
            };
            append(cell.value(), attributes);
            setToolTipText(cell.origin() == Origin.DEFAULT ? "Vem do default: " + cell.value() : cell.value());
        }
    }
}
