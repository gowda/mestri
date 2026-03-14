plugins {
  kotlin("jvm") version "2.3.10" apply false
  kotlin("plugin.spring") version "2.3.10" apply false
  id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
  id("org.springframework.boot") version "4.0.3" apply false
  id("io.spring.dependency-management") version "1.1.7" apply false
  `java-library`
}

allprojects {
  group = providers.gradleProperty("group").get()
  version = providers.gradleProperty("version").get()

  repositories {
    mavenCentral()
  }
}

subprojects {
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jlleitschuh.gradle.ktlint")
  apply(plugin = "java-library")
  apply(plugin = "io.spring.dependency-management")

  java {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      freeCompilerArgs.add("-Xjsr305=strict")
    }
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }

  extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
    imports {
      mavenBom(
        "org.springframework.boot:spring-boot-dependencies:${rootProject.providers.gradleProperty("springBootVersion").get()}"
      )
    }
  }

  dependencies {
    "implementation"(kotlin("reflect"))
    "testImplementation"(platform("org.junit:junit-bom:5.14.3"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"(kotlin("test"))
  }
}
