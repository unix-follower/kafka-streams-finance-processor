subprojects {
  group = "com.example.finprocessor"
  version = System.getenv("APP_VERSION") ?: "2024.03.1-SNAPSHOT"

  repositories {
    mavenCentral()
  }
}
