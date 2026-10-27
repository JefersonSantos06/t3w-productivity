package com.github.jefersonsantos06.t3wproductivity.listeners;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

@FunctionalInterface
public interface EnvironmentSettingsListener {

    Topic<EnvironmentSettingsListener> TOPIC = Topic.create("Environment settings changed", EnvironmentSettingsListener.class);

    void environmentSettingsChanged();

    /**
     * Publica no bus da aplicação para que todos os projetos abertos (ex.: outras worktrees)
     * recebam a alteração, já que variáveis globais e presets são compartilhados.
     */
    static void publish() {
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(EnvironmentSettingsListener.TOPIC)
                .environmentSettingsChanged();
    }

    static void subscribe(final Project project, final Disposable disposable, final EnvironmentSettingsListener listener) {
        project.getMessageBus()
                .connect(disposable)
                .subscribe(EnvironmentSettingsListener.TOPIC, listener);
    }
}
