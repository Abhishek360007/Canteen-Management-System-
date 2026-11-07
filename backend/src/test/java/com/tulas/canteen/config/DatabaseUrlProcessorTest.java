package com.tulas.canteen.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlProcessorTest {

    @Test
    void convertsNeonConnectionString() {
        Map<String, Object> p = DatabaseUrlProcessor.convert(
                "postgresql://neondb_owner:p%40ss:w0rd@ep-cool-river-a1b2c3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertThat(p.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://ep-cool-river-a1b2c3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require");
        assertThat(p.get("spring.datasource.username")).isEqualTo("neondb_owner");
        assertThat(p.get("spring.datasource.password")).isEqualTo("p@ss:w0rd");
    }

    @Test
    void keepsPortAndAcceptsJdbcUrls() {
        assertThat(DatabaseUrlProcessor.convert("postgres://u:p@localhost:5433/db").get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5433/db");
        assertThat(DatabaseUrlProcessor.convert("jdbc:postgresql://h/db").get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://h/db");
        assertThatThrownBy(() -> DatabaseUrlProcessor.convert("redis://x")).isInstanceOf(IllegalArgumentException.class);
    }
}
