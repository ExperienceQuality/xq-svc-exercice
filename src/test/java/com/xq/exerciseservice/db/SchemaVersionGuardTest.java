package com.xq.exerciseservice.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class SchemaVersionGuardTest {
    @Test
    void runPassesWhenQueriedVersionMeetsMinimum() {
        SchemaVersionGuard guard = guardReturning(1, 1);
        assertDoesNotThrow(() -> guard.run(null));
    }

    @Test
    void runFailsWhenQueriedVersionIsBelowMinimum() {
        SchemaVersionGuard guard = guardReturning(0, 1);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> guard.run(null));
        assertTrue(ex.getMessage().contains("below required minimum"));
    }

    @Test
    void runFailsWhenHistoryIsEmpty() {
        SchemaVersionGuard guard = guardReturning(0, 1);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> guard.run(null));
        assertTrue(ex.getMessage().contains("version 0"));
    }

    @Test
    void rejectsUnsafeSchemaIdentifier() {
        JdbcClient jdbc = mock(JdbcClient.class);
        assertThrows(
                IllegalStateException.class,
                () -> new SchemaVersionGuard(jdbc, 1, "exercise;drop"));
    }

    @Test
    void queriesFlywayHistoryInConfiguredSchema() {
        JdbcClient jdbc = mockJdbcReturning(2);
        SchemaVersionGuard guard = new SchemaVersionGuard(jdbc, 1, "exercise");
        assertDoesNotThrow(() -> guard.run(null));
        verify(jdbc).sql(contains("exercise.flyway_schema_history"));
    }

    private static SchemaVersionGuard guardReturning(int version, int minVersion) {
        return new SchemaVersionGuard(mockJdbcReturning(version), minVersion, "exercise");
    }

    @SuppressWarnings("unchecked")
    private static JdbcClient mockJdbcReturning(int version) {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<Integer> mapped = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.query(Integer.class)).thenReturn(mapped);
        when(mapped.single()).thenReturn(version);
        return jdbc;
    }
}
