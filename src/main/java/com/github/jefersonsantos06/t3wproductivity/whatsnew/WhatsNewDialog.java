package com.github.jefersonsantos06.t3wproductivity.whatsnew;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

/**
 * Novidades da versão instalada, lidas das change notes do próprio plugin (geradas a partir do CHANGELOG.md).
 */
public final class WhatsNewDialog extends DialogWrapper {

    private final String changeNotes;

    private WhatsNewDialog(@Nullable final Project project, final String version, final String changeNotes) {
        super(project);
        this.changeNotes = changeNotes;
        setTitle("O Que Há De Novo No T3W Productivity " + version);
        setOKButtonText("Fechar");
        init();
    }

    /**
     * Abre a janela com as novidades da versão instalada; sem change notes, não faz nada.
     */
    public static void showForCurrentVersion(@Nullable final Project project) {
        WhatsNewService.getInstance().currentPlugin()
                .ifPresent(plugin -> new WhatsNewDialog(project, plugin.getVersion(), plugin.getChangeNotes()).show());
    }

    @Override
    protected JComponent createCenterPanel() {
        final var content = new JEditorPane();
        content.setEditorKit(HTMLEditorKitBuilder.simple());
        content.setText(changeNotes);
        content.setEditable(false);
        content.setCaretPosition(0);
        content.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getURL() != null) {
                BrowserUtil.browse(event.getURL().toString());
            }
        });
        final var scrollPane = new JBScrollPane(content);
        scrollPane.setPreferredSize(JBUI.size(560, 360));
        return scrollPane;
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{ getOKAction() };
    }
}
