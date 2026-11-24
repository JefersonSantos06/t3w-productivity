package com.github.jefersonsantos06.t3wproductivity.webtypes;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Só usa a plataforma, então também roda no IntelliJ Community (task testCommunity), onde os web-types não têm efeito
 * porque não há plugin JavaScript.
 */
public class VaadinWebTypesSmokeTest extends BasePlatformTestCase {

    public void testEditsHtmlWithVaadinTagsWithoutErrors() {
        myFixture.configureByText("index.html", "<vaadin-text-field label=\"Nome\"></vaadin-text-field>\n<vaadin-<caret>");

        myFixture.doHighlighting();
        myFixture.completeBasic();
    }
}
