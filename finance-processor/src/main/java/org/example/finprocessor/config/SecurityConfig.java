package org.example.finprocessor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.ServerWebExchange;

@Configuration
class SecurityConfig {
    private final AppProperties appProperties;

    SecurityConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .cors(corsSpec -> corsSpec.configurationSource(this::createCorsConfig))
            .csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }

    private CorsConfiguration createCorsConfig(ServerWebExchange exchange) {
        final var cors = appProperties.cors();
        final var corsConfiguration = new CorsConfiguration();

        corsConfiguration.addAllowedOrigin(cors.accessControlAllowOrigin());

        if (cors.accessControlMaxAge() != null) {
            corsConfiguration.setMaxAge(cors.accessControlMaxAge());
        }

        if (cors.accessControlAllowHeaders() != null) {
            corsConfiguration.setAllowedHeaders(
                cors.accessControlAllowHeaders().stream().toList()
            );
        }
        return corsConfiguration;
    }
}
