package com.tulas.canteen;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

/** The online payment tests again, on a real PostgreSQL server (the database Neon runs). */
class PostgresPaymentFlowTest extends PaymentFlowTest {

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
