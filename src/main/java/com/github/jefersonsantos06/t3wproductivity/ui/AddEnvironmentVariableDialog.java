package com.github.jefersonsantos06.t3wproductivity.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.Collection;
import java.util.Set;

/**
 * Adiciona uma variável pela Tool Window, gravando como override do projeto (padrão) ou no Global.
 */
public final class AddEnvironmentVariableDialog extends DialogWrapper {

    private final ComboBox<String> keyField;
    private final JBTextField valueField = new JBTextField();
    private final JBRadioButton projectDestination = new JBRadioButton("Projeto (só este projeto/worktree)", true);
    private final JBRadioButton globalDestination = new JBRadioButton("Global (todos os projetos)");
    private final Set<String> projectKeys;
    private final Set<String> globalKeys;
    private final Set<String> keysOverridingGlobal;

    /**
     * @param keysOverridingGlobal chaves do preset ativo e do projeto, que escondem um valor global de mesmo nome
     */
    public AddEnvironmentVariableDialog(final Project project,
                                        final Collection<String> suggestedKeys,
                                        final Set<String> projectKeys,
                                        final Set<String> globalKeys,
                                        final Set<String> keysOverridingGlobal) {
        super(project);
        this.projectKeys = projectKeys;
        this.globalKeys = globalKeys;
        this.keysOverridingGlobal = keysOverridingGlobal;

        keyField = new ComboBox<>(suggestedKeys.toArray(String[]::new));
        keyField.setEditable(true);
        keyField.setSelectedItem("");

        final var destinationGroup = new ButtonGroup();
        destinationGroup.add(projectDestination);
        destinationGroup.add(globalDestination);

        setTitle("Adicionar Variável De Ambiente");
        init();
        initValidation();
    }

    public String getKey() {
        final var editor = keyField.getEditor().getEditorComponent();
        final var text = editor instanceof JTextComponent textComponent
                ? textComponent.getText()
                : String.valueOf(keyField.getEditor().getItem());
        return StringUtils.trimToEmpty(text);
    }

    public String getValue() {
        return valueField.getText();
    }

    public boolean isGlobal() {
        return globalDestination.isSelected();
    }

    @Override
    protected JComponent createCenterPanel() {
        final var destinationPanel = new JPanel();
        destinationPanel.setLayout(new BoxLayout(destinationPanel, BoxLayout.Y_AXIS));
        destinationPanel.add(projectDestination);
        destinationPanel.add(globalDestination);

        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Chave:", keyField)
                .addLabeledComponent("Valor:", valueField)
                .addLabeledComponent("Destino:", destinationPanel)
                .getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return keyField;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        final var key = getKey();
        if (key.isEmpty()) {
            return new ValidationInfo("Informe a chave.", keyField);
        }
        if (key.chars().anyMatch(character -> Character.isWhitespace(character) || character == '=')) {
            return new ValidationInfo("A chave não pode ter espaços nem '='.", keyField);
        }
        if (!isGlobal() && projectKeys.contains(key)) {
            return new ValidationInfo("Este projeto já tem override desta chave; o valor será substituído.", keyField)
                    .asWarning().withOKEnabled();
        }
        if (isGlobal() && keysOverridingGlobal.contains(key)) {
            return new ValidationInfo("O preset ativo ou um override do projeto define esta chave; o valor global não terá efeito aqui.", keyField)
                    .asWarning().withOKEnabled();
        }
        if (isGlobal() && globalKeys.contains(key)) {
            return new ValidationInfo("Esta chave já existe no Global; o valor será substituído.", keyField)
                    .asWarning().withOKEnabled();
        }
        return null;
    }
}
