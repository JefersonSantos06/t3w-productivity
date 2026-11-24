package com.github.jefersonsantos06.t3wproductivity.webtypes;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Depende dos plugins de CSS e JavaScript, que não existem no IntelliJ Community.
 */
public class VaadinWebTypesCssTest extends BasePlatformTestCase {

    public void testCompletesVaadinTagsInSelectors() {
        myFixture.configureByText("styles.css", "vaadin-text<caret> {}");
        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "vaadin-text-field", "vaadin-text-area");
    }

    public void testCompletesShadowParts() {
        myFixture.configureByText("styles.css", "vaadin-text-field::part(<caret>) {}");
        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "input-field", "error-message");
    }

    public void testCompletesStateAttributes() {
        myFixture.configureByText("styles.css", "vaadin-text-field[has-<caret>] {}");
        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "has-value", "has-label");
    }

    public void testCompletesLumoCustomProperties() {
        myFixture.configureByText("styles.css", "a { color: var(--lumo-primary-<caret>); }");
        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "--lumo-primary-color", "--lumo-primary-text-color");
    }
}
