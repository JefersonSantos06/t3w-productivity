package com.github.jefersonsantos06.t3wproductivity.listeners;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

@FunctionalInterface
public interface EnvironmentSettingsListener {

    Topic<EnvironmentSettingsListener> TOPIC = Topic.create("Environment settings changed", EnvironmentSettingsListener.class);

    void environmentSettingsChanged();

    static void publish(final Project project) {
        project.getMessageBus()
                .syncPublisher(EnvironmentSettingsListener.TOPIC)
                .environmentSettingsChanged();
    }

    static void subscribe(final Project project, final Disposable disposable, final EnvironmentSettingsListener listener) {
        project.getMessageBus()
                .connect(disposable)
                .subscribe(EnvironmentSettingsListener.TOPIC, listener);
    }
}
