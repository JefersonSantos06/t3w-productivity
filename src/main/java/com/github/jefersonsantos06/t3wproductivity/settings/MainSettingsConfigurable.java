package com.github.jefersonsantos06.t3wproductivity.settings;

import com.github.jefersonsantos06.t3wproductivity.listeners.FlywaySettingsListener;
import com.github.jefersonsantos06.t3wproductivity.services.project.FlywayProjectService;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Optional;

public final class MainSettingsConfigurable implements Configurable {

    private final Project project;
    private SettingsComponent component;

    public MainSettingsConfigurable(final Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "T3W Productivity";
    }

    @Override
    public @NotNull JComponent createComponent() {
        component = new SettingsComponent();
        reset();
        return component.getPanel();
    }

    @Override
    public boolean isModified() {
        final var service = project.getService(FlywayProjectService.class);
        return component != null && !service.getMigrationsDirectory().equals(component.getMigrationsDirectory());
    }

    @Override
    public void apply() {
        if (component == null) {
            return;
        }
        final var service = project.getService(FlywayProjectService.class);
        final var newPath = component.getMigrationsDirectory();
        service.setMigrationsDirectory(newPath);
        FlywaySettingsListener.publish(project, newPath);
    }

    @Override
    public void reset() {
        if (component == null) {
            return;
        }
        final var service = project.getService(FlywayProjectService.class);
        component.setMigrationsDirectory(service.getMigrationsDirectory());
    }

    private static final class SettingsComponent {

        private final JPanel panel;
        private final TextFieldWithBrowseButton migrationsDirectoryField;

        public SettingsComponent() {
            this.migrationsDirectoryField = new TextFieldWithBrowseButton();
            this.panel = FormBuilder.createFormBuilder()
                    .addLabeledComponent("Pasta de migrations:", migrationsDirectoryField)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();

            configureBrowseButton();
        }

        public JPanel getPanel() {
            return panel;
        }

        public String getMigrationsDirectory() {
            return StringUtils.trimToEmpty(migrationsDirectoryField.getText());
        }

        public void setMigrationsDirectory(String value) {
            migrationsDirectoryField.setText(value);
        }

        private void configureBrowseButton() {
            migrationsDirectoryField.addActionListener(event -> {
                final var descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
                descriptor.setTitle("Pasta De Migrations");
                descriptor.setDescription("Selecione a pasta onde os arquivos Flyway serao criados.");
                Optional.ofNullable(FileChooser.chooseFile(descriptor, null, null))
                        .ifPresent(selected -> migrationsDirectoryField.setText(selected.getPath()));
            });
        }
    }
}
