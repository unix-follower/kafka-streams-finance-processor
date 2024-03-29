rootProject.name = "kafka-streams-finance-processor"

include(
    "finance-predictor-api",
    "finance-predictor",
    "finance-processor"
)

pluginManagement {
    plugins {
        id("org.springframework.boot") version "3.2.0"
        id("io.spring.dependency-management") version "1.1.4"
    }
}
