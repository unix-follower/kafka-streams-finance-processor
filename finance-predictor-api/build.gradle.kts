plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.projectreactor:reactor-core")
}

tasks {
    bootJar {
        enabled = false
    }
}
