package com.github.jefersonsantos06.t3wproductivity.toolWindow;

import com.github.jefersonsantos06.t3wproductivity.MyBundle;
import com.github.jefersonsantos06.t3wproductivity.services.MyProjectService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JPanel;

public final class MyToolWindowFactory implements ToolWindowFactory {
    private static final Logger LOG = Logger.getInstance(MyToolWindowFactory.class);

    public MyToolWindowFactory() {
        LOG.warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        MyToolWindow myToolWindow = new MyToolWindow(project);
        Content content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    private static final class MyToolWindow {
        private final MyProjectService service;

        private MyToolWindow(@NotNull Project project) {
            this.service = project.getService(MyProjectService.class);
        }

        private @NotNull JPanel getContent() {
            JBPanel<?> panel = new JBPanel<>();
            JBLabel label = new JBLabel(MyBundle.message("randomLabel", "?"));
            JButton shuffleButton = new JButton(MyBundle.message("shuffle"));
            shuffleButton.addActionListener(event -> label.setText(MyBundle.message("randomLabel", service.getRandomNumber())));

            panel.add(label);
            panel.add(shuffleButton);
            return panel;
        }
    }
}
