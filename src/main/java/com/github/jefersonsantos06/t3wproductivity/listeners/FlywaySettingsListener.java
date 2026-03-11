package com.github.jefersonsantos06.t3wproductivity.listeners;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

@FunctionalInterface
public interface FlywaySettingsListener {

    Topic<FlywaySettingsListener> TOPIC = Topic.create("Flyway settings changed", FlywaySettingsListener.class);

    void migrationsDirectoryChanged(String newPath);

    static void publish(final Project project, final String newPath) {
        project.getMessageBus()
                .syncPublisher(FlywaySettingsListener.TOPIC)
                .migrationsDirectoryChanged(newPath);
    }

    static void subscribe(final Project project,final  Disposable disposable, final FlywaySettingsListener listener) {
        project.getMessageBus()
                .connect(disposable)
                .subscribe(FlywaySettingsListener.TOPIC, listener);
    }
}
