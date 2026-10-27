package com.github.jefersonsantos06.t3wproductivity.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class MainToolWindowLayoutTest extends BasePlatformTestCase {

    public void testTableKeepsHeightInNarrowToolWindow() {
        assertTableFills(280, 700);
    }

    public void testTableGrowsWithToolWindowHeight() {
        final var shortHeight = assertTableFills(320, 600);
        final var tallHeight = assertTableFills(320, 900);
        assertEquals(300, tallHeight - shortHeight);
    }

    private int assertTableFills(final int width, final int height) {
        final var disposable = Disposer.newDisposable();
        Disposer.register(getTestRootDisposable(), disposable);
        final var panel = MainToolWindowFactory.createPanel(getProject(), disposable);
        panel.setSize(width, height);
        layoutRecursively(panel);

        final var table = UIUtil.findComponentOfType(panel, JBTable.class);
        assertNotNull(table);
        final var scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        assertTrue("Tabela com altura " + scrollPane.getHeight() + " em " + width + "x" + height,
                scrollPane.getHeight() > 150);
        return scrollPane.getHeight();
    }

    private static void layoutRecursively(final Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (final var child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }
}
