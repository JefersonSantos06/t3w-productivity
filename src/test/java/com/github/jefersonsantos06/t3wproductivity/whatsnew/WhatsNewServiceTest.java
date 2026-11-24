package com.github.jefersonsantos06.t3wproductivity.whatsnew;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class WhatsNewServiceTest extends BasePlatformTestCase {

    public void testShowsNewsOncePerVersion() {
        final var service = new WhatsNewService();

        assertTrue(service.markSeen("0.3.0"));
        assertFalse(service.markSeen("0.3.0"));
        assertTrue(service.markSeen("0.4.0"));
    }

    public void testStartupActivityProvidesChangeNotesFromChangelog() {
        final var descriptor = PluginManagerCore.getPlugin(PluginId.getId("com.github.jefersonsantos06.t3wproductivity"));
        assertNotNull(descriptor);

        new WhatsNewStartupActivity().setPluginDescriptor(descriptor);

        final var plugin = WhatsNewService.getInstance().currentPlugin().orElseThrow();
        assertTrue(plugin.getChangeNotes(), plugin.getChangeNotes().contains("O que há de novo"));
    }
}
