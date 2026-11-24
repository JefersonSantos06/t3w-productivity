package com.github.jefersonsantos06.t3wproductivity.database;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Roda com o plugin Database carregado; garante que o descritor opcional sobe e que, sem data sources locais, nada é
 * disparado.
 */
public class IntrospectionRefreshServiceTest extends BasePlatformTestCase {

    public void testDoesNothingWithoutLocalDataSources() {
        final var service = IntrospectionRefreshService.getInstance();
        final var result = new FlywayLogParser.Result(new JdbcLocation("localhost", 5432, "wmx"), "public", "202611241300", 1);

        service.onFlywayFinished(getProject(), result);
        service.introspectMissingDatabases(getProject());
    }
}
