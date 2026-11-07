package com.tulas.canteen.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets you paste a hosted database's connection string as-is into DATABASE_URL, e.g. Neon's
 * {@code postgresql://user:password@ep-xyz.aws.neon.tech/neondb?sslmode=require}.
 * It is converted into the JDBC URL, username and password that Spring expects.
 */
public class DatabaseUrlProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String raw = env.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank()) return;
        env.getPropertySources().addFirst(new MapPropertySource("databaseUrl", convert(raw.trim())));
    }

    static Map<String, Object> convert(String raw) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (raw.startsWith("jdbc:")) {
            props.put("spring.datasource.url", raw);
            return props;
        }
        URI uri = URI.create(raw);
        String scheme = uri.getScheme();
        String driver = switch (scheme) {
            case "postgres", "postgresql" -> "postgresql";
            case "mysql" -> "mysql";
            default -> throw new IllegalArgumentException("DATABASE_URL must start with postgresql:// or mysql://");
        };
        StringBuilder jdbc = new StringBuilder("jdbc:").append(driver).append("://").append(uri.getHost());
        if (uri.getPort() > 0) jdbc.append(':').append(uri.getPort());
        jdbc.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
        if (uri.getRawQuery() != null) {
            // pgJDBC doesn't understand libpq's channel_binding option; drop it, keep the rest (e.g. sslmode).
            String query = String.join("&", java.util.Arrays.stream(uri.getRawQuery().split("&"))
                    .filter(p -> !p.startsWith("channel_binding=")).toList());
            if (!query.isEmpty()) jdbc.append('?').append(query);
        }
        props.put("spring.datasource.url", jdbc.toString());

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
            props.put("spring.datasource.username", URLDecoder.decode(user, StandardCharsets.UTF_8));
            if (colon >= 0) {
                props.put("spring.datasource.password",
                        URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8));
            }
        }
        return props;
    }
}
