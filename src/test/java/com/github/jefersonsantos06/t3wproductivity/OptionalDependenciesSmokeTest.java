package com.github.jefersonsantos06.t3wproductivity;

import com.github.jefersonsantos06.t3wproductivity.settings.MainSettingsConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Roda no IntelliJ (./gradlew test), no Community (./gradlew testCommunity) e no DataGrip (./gradlew testDataGrip):
 * cada IDE carrega só as partes cujas dependências opcionais (Java, Database) ela tem.
 */
public class OptionalDependenciesSmokeTest extends BasePlatformTestCase {

    public void testLoadsOnlyWhatTheIdeSupports() {
        final var hasJava = PluginManagerCore.isPluginInstalled(PluginId.getId("com.intellij.java"));
        final var hasDatabase = PluginManagerCore.isPluginInstalled(PluginId.getId("com.intellij.database"));
        final var actions = ActionManager.getInstance();

        assertNotNull(actions.getAction("t3w.productivity.whatsNew"));
        assertNotNull(actions.getAction("t3w.productivity.createFlywayMigration"));
        assertEquals(hasJava, actions.getAction("t3w.productivity.migratePreparedStatementToNamed") != null);
        assertEquals(hasJava, actions.getAction("t3w.productivity.addExceptionPoint") != null);
        assertEquals(hasJava, ToolWindowEP.EP_NAME.getExtensionList().stream().anyMatch(ep -> "T3W Productivity".equals(ep.id)));
        assertEquals(hasDatabase, NotificationGroupManager.getInstance().getNotificationGroup("T3W Database") != null);
    }

    public void testMainSettingsOpenWithoutJavaClasses() {
        final var configurable = new MainSettingsConfigurable(getProject());
        try {
            assertNotNull(configurable.createComponent());
            assertFalse(configurable.isModified());
        } finally {
            configurable.disposeUIResources();
        }
    }
}
