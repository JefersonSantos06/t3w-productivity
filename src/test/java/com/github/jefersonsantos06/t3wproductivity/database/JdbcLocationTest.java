package com.github.jefersonsantos06.t3wproductivity.database;

import junit.framework.TestCase;

import java.util.Optional;

public class JdbcLocationTest extends TestCase {

    public void testParsesHostPortAndDatabase() {
        assertEquals(Optional.of(new JdbcLocation("localhost", 5433, "wmx")),
                JdbcLocation.parse("jdbc:postgresql://LocalHost:5433/wmx?ApplicationName=jbear-admin&sslmode=disable"));
    }

    public void testUsesDefaultPortAndAllowsMissingDatabase() {
        assertEquals(Optional.of(new JdbcLocation("localhost", 5432, "")), JdbcLocation.parse("jdbc:postgresql://localhost"));
        assertEquals(Optional.of(new JdbcLocation("[::1]", 5432, "t3w")), JdbcLocation.parse("jdbc:postgresql://[::1]/t3w"));
    }

    public void testRejectsOtherDatabases() {
        assertEquals(Optional.empty(), JdbcLocation.parse("jdbc:mysql://localhost:3306/wmx"));
        assertEquals(Optional.empty(), JdbcLocation.parse(null));
    }

    public void testTreatsLoopbackAliasesAsTheSameLocalServer() {
        final var flyway = JdbcLocation.parse("jdbc:postgresql://localhost:5432/wmx").orElseThrow();

        assertTrue(flyway.isSameLocalServer(JdbcLocation.parse("jdbc:postgresql://127.0.0.1:5432/t3w").orElseThrow()));
        assertFalse(flyway.isSameLocalServer(JdbcLocation.parse("jdbc:postgresql://localhost:5433/wmx").orElseThrow()));
    }

    public void testNeverMatchesRemoteServers() {
        final var production = JdbcLocation.parse("jdbc:postgresql://10.115.160.12:5432/wmx?sslmode=require").orElseThrow();

        assertFalse(production.isLocal());
        assertFalse(production.isSameLocalServer(JdbcLocation.parse("jdbc:postgresql://localhost:5432/wmx").orElseThrow()));
    }
}
