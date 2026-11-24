package com.github.jefersonsantos06.t3wproductivity.webtypes;

import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Depende do plugin JavaScript (Ultimate), que é quem liga os web-types embutidos ao HTML.
 */
public class VaadinWebTypesHtmlTest extends BasePlatformTestCase {

    private static final PluginId PLUGIN_ID = PluginId.getId("com.github.jefersonsantos06.t3wproductivity");

    public void testRegistersVaadinWebTypes() {
        ExtensionPoint<Object> webTypes = ApplicationManager.getApplication().getExtensionArea()
                .getExtensionPointIfRegistered("com.intellij.polySymbols.webTypes");

        assertNotNull(webTypes);
        assertTrue(webTypes.getExtensionList().stream()
                .anyMatch(extension -> extension instanceof BaseKeyedLazyInstance<?> bean
                        && PLUGIN_ID.equals(bean.getPluginDescriptor().getPluginId())));
    }

    public void testCompletesVaadinTags() {
        myFixture.configureByText("index.html", "<vaadin-text<caret>");
        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "vaadin-text-field", "vaadin-text-area");
    }

    public void testCompletesComponentAttributes() {
        myFixture.configureByText("index.html", "<vaadin-text-field <caret>></vaadin-text-field>");
        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "clear-button-visible", "helper-text");
    }

    public void testHighlightsOnlyUnknownAttributes() {
        myFixture.enableInspections(new HtmlUnknownTagInspection(), new HtmlUnknownAttributeInspection());
        myFixture.configureByText("index.html", """
                <vaadin-text-field label="Nome" clear-button-visible <warning descr="Attribute atributo-inexistente is not allowed here">atributo-inexistente</warning>></vaadin-text-field>
                """);

        myFixture.checkHighlighting();
    }
}
