plugins {
  java
  checkstyle
  jacoco
  id("com.diffplug.spotless") version "6.25.0"
  id("org.springframework.boot") version "3.5.14"
  id("io.spring.dependency-management") version "1.1.7"
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

val mapstructVersion = "1.6.3"
val springdocVersion = "2.8.17"

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
      // Enforce 70 % line coverage on all business-logic classes.
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
        minimum = "0.70".toBigDecimal()
      }
    }
  }
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}
