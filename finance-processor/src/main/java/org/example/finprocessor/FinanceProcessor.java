package org.example.finprocessor;

import org.example.finprocessor.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Hooks;
import reactor.tools.agent.ReactorDebugAgent;

@SpringBootApplication(exclude = {ReactiveUserDetailsServiceAutoConfiguration.class})
@EnableConfigurationProperties({AppProperties.class})
public class FinanceProcessor {
    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        initDebugAgentsIfNotProd();

        SpringApplication.run(FinanceProcessor.class, args);
    }

    static void initDebugAgentsIfNotProd() {
        if (!isProdEnv(System.getenv("SPRING_PROFILES_ACTIVE"))) {
            final var appBlockHoundEnabled = System.getenv("APP_BLOCK_HOUND_ENABLED");
            if (isBlockHoundEnabled(appBlockHoundEnabled)) {
                BlockHound.builder()
                    .allowBlockingCallsInside(
                        "org.springframework.util.ConcurrentReferenceHashMap$ReferenceManager",
                        "pollForPurge"
                    )
                    .allowBlockingCallsInside(
                        "org.springframework.web.reactive.DispatcherHandler",
                        "handle"
                    )
                    .install();
            }
            ReactorDebugAgent.init();
        }
    }

    static boolean isProdEnv(String activeProfile) {
        return "prod".equals(activeProfile);
    }

    static boolean isBlockHoundEnabled(String booleanValue) {
        return booleanValue == null || Boolean.parseBoolean(booleanValue);
    }
}
