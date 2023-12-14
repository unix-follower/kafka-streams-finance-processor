plugins {
    java
    jacoco
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

private val logbookVersion = "3.7.1"

dependencies {
    implementation(project(":finance-predictor-api"))
    implementation(project(":finance-predictor"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-streams")
    implementation("io.projectreactor.netty:reactor-netty-http")
    implementation("io.projectreactor.kafka:reactor-kafka")
    implementation("io.projectreactor:reactor-tools")
    implementation("io.projectreactor.tools:blockhound:1.0.8.RELEASE")
    implementation("org.zalando:logbook-spring-boot-starter:$logbookVersion")
    implementation("org.zalando:logbook-netty:$logbookVersion")
    implementation("org.zalando:logbook-spring-webflux:$logbookVersion")
    implementation("com.opencsv:opencsv:5.9")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.apache.kafka:kafka-streams-test-utils")
    testImplementation("io.projectreactor:reactor-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
    this.jvmArgs = listOf("-XX:+AllowRedefinitionToAddDeleteMethods")
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    val matchingFileTree = sourceSets.main.get().output.asFileTree.matching {
        exclude("org/example/finprocessor/config/")
    }
    classDirectories.setFrom(matchingFileTree)
}

tasks.jacocoTestReport {
    reports {
        xml.required = false
        csv.required = false
    }
}
