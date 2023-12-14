package org.example.finprocessor.config;

import java.time.Duration;
import java.util.Set;

public record CorsProperties(
    String accessControlAllowOrigin,
    Duration accessControlMaxAge,
    Set<String> accessControlAllowHeaders
) {
}
