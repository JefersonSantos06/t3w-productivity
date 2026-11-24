package com.github.jefersonsantos06.t3wproductivity.whatsnew;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

/**
 * Na primeira abertura de projeto depois de instalar ou atualizar o plugin, avisa que há novidades.
 * Recebe o próprio descritor via {@link PluginAware} (as formas de buscá-lo por id são internas na plataforma).
 */
public final class WhatsNewStartupActivity implements ProjectActivity, PluginAware {

    private static final String NOTIFICATION_GROUP = "T3W Novidades";

    @Override
    public void setPluginDescriptor(@NotNull final PluginDescriptor pluginDescriptor) {
        WhatsNewService.getInstance().setPlugin(pluginDescriptor);
    }

    @Override
    public Object execute(@NotNull final Project project, @NotNull final Continuation<? super Unit> continuation) {
        final var service = WhatsNewService.getInstance();
        service.currentPlugin()
                .filter(plugin -> service.markSeen(plugin.getVersion()))
                .ifPresent(plugin -> NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                        .createNotification("T3W Productivity atualizado para " + plugin.getVersion(),
                                "Veja o que mudou nesta versão.", NotificationType.INFORMATION)
                        .addAction(NotificationAction.createSimpleExpiring("Ver novidades",
                                () -> WhatsNewDialog.showForCurrentVersion(project)))
                        .notify(project));
        return Unit.INSTANCE;
    }
}
