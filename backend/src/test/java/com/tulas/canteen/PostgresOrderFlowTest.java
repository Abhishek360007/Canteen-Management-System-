package com.tulas.canteen;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

/**
 * Runs the same end-to-end order flow against a real PostgreSQL server (the database Neon runs),
 * to catch SQL that works on H2 but not on Postgres.
 */
class PostgresOrderFlowTest extends OrderFlowTest {

    private static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
    }
}
