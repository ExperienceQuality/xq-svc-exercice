package com.xq.exerciseservice.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchemaVersionCheckerTest {
    @Test
    void acceptsVersionAtOrAboveMinimum() {
        assertDoesNotThrow(() -> SchemaVersionChecker.requireAtLeast(1, 1));
        assertDoesNotThrow(() -> SchemaVersionChecker.requireAtLeast(2, 1));
    }

    @Test
    void rejectsVersionBelowMinimum() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> SchemaVersionChecker.requireAtLeast(0, 1));
        assertTrue(ex.getMessage().contains("below required minimum"));
    }

    @Test
    void rejectsEmptyHistoryAsVersionZero() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> SchemaVersionChecker.requireAtLeast(0, 1));
        assertTrue(ex.getMessage().contains("version 0"));
    }
}
