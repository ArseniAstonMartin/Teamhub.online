plugins {
  java
  checkstyle
  jacoco
  id("com.diffplug.spotless") version "6.25.0"
  id("org.sonarqube") version "7.3.0.8198"
  id("org.springframework.boot") version "3.5.14"
  id("io.spring.dependency-management") version "1.1.7"
  // Uploads source context to Sentry so stack traces show your source lines.
  // Only active when SENTRY_AUTH_TOKEN env var is set (skipped in local dev if absent).
  id("io.sentry.jvm.gradle") version "6.9.0"
}

group = "com.teamhub"
version = "0.0.1-SNAPSHOT"
description = "Teamhub.online backend service"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories {
  mavenCentral()
}

dependencyLocking {
  lockAllConfigurations()
}

val mapstructVersion = "1.6.3"
val springdocVersion = "2.8.17"
val shedlockVersion = "6.6.0"
val sentryVersion = "7.22.0"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.flywaydb:flyway-core")
  implementation("org.hibernate.orm:hibernate-spatial")
  implementation("org.mapstruct:mapstruct:$mapstructVersion")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
  implementation("net.javacrumbs.shedlock:shedlock-spring:$shedlockVersion")
  implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:$shedlockVersion")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:$sentryVersion")

  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
  annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.mockito:mockito-junit-jupiter")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.testcontainers:postgresql")
  testImplementation("org.postgresql:postgresql")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
  java {
    googleJavaFormat("1.25.2")
    formatAnnotations()
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    ktlint("1.5.0")
  }
}

checkstyle {
  toolVersion = "10.21.4"
  configFile = file("config/checkstyle/checkstyle.xml")
  maxWarnings = 0
}

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required = true
    html.required = true
  }
}

tasks.jacocoTestCoverageVerification {
  violationRules {
    rule {
      // Enforce 80 % line coverage on all business-logic classes.
      // Excludes generated code (MapStruct mappers, Lombok, Spring config).
      excludes =
        listOf(
          "com.teamhub.TeamhubApplication",
          "com.teamhub.config.*",
          "com.teamhub.*MapperImpl",
        )
      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = "0.80".toBigDecimal()
      }
    }
  }
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}

// Sonar task must run after the JaCoCo XML report is generated.
tasks.named("sonar") {
  dependsOn(tasks.jacocoTestReport)
}

sonar {
  properties {
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.organization", "teamhub")
    property("sonar.projectKey", "ArseniAstonMartin_Teamhub.online")
    // Reuse the JaCoCo XML report already produced by the jacocoTestReport task.
    property(
      "sonar.coverage.jacoco.xmlReportPaths",
      "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml",
    )
    property("sonar.java.source", "21")
    // Fail CI if the SonarCloud Quality Gate is not green.
    property("sonar.qualitygate.wait", "true")
  }
}

sentry {
  // Uploads Java source files to Sentry for inline source context in stack traces.
  // Only runs when SENTRY_AUTH_TOKEN is present; skipped silently in local dev.
  includeSourceContext = true
  org = "teamhub-gs"
  projectName = "java-spring-boot"
  authToken = System.getenv("SENTRY_AUTH_TOKEN")
}
