package com.xq.exerciseservice.db;

/**
 * Fail-fast check that the database Flyway schema version meets the configured minimum.
 */
public final class SchemaVersionChecker {
    private SchemaVersionChecker() {
    }

    public static void requireAtLeast(int currentVersion, int minimumVersion) {
        if (currentVersion < minimumVersion) {
            throw new IllegalStateException(
                    "Database schema version " + currentVersion
                            + " is below required minimum " + minimumVersion);
        }
    }
}
