plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":finance-predictor-api"))
    implementation("org.springframework:spring-core")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-webflux")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.netty:netty-handler")
}

tasks {
    bootJar {
        enabled = false
    }
}
