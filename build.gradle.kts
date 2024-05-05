subprojects {
  group = "com.example.finprocessor"
  version = System.getenv("APP_VERSION") ?: "2024.05.0"

  repositories {
    mavenCentral()
  }
}
