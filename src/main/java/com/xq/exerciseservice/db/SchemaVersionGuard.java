package com.xq.exerciseservice.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * F2 startup guard: aborts boot when flyway_schema_history is below the pinned minimum.
 */
@Component
public class SchemaVersionGuard implements ApplicationRunner {
    private final JdbcClient jdbc;
    private final int minVersion;
    private final String schema;

    public SchemaVersionGuard(
            JdbcClient jdbc,
            @Value("${xq.db.schema.min-version}") int minVersion,
            @Value("${spring.datasource.hikari.schema:exercise}") String schema) {
        this.jdbc = jdbc;
        this.minVersion = minVersion;
        this.schema = requireSafeIdentifier(schema);
    }

    @Override
    public void run(ApplicationArguments args) {
        SchemaVersionChecker.requireAtLeast(currentVersion(), minVersion);
    }

    int currentVersion() {
        String sql = """
                select coalesce(max(cast(version as integer)), 0)
                from %s.flyway_schema_history
                where success and version is not null
                """.formatted(schema);
        return jdbc.sql(sql).query(Integer.class).single();
    }

    private static String requireSafeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("Invalid database schema name: " + identifier);
        }
        return identifier;
    }
}
