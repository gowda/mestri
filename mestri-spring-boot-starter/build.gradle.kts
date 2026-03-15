plugins {
  kotlin("plugin.spring")
  id("io.spring.dependency-management")
}

dependencies {
  api(project(":mestri-core"))
  api(project(":mestri-redis"))
  api(project(":mestri-dashboard"))
  api(project(":mestri-jobrunr-compat"))
  api("org.springframework.boot:spring-boot-starter-actuator")
  api("org.springframework.boot:spring-boot-starter-data-redis")
  api("org.springframework.boot:spring-boot-starter-json")
  api("org.springframework.boot:spring-boot-autoconfigure")
  compileOnly("org.springframework.boot:spring-boot-configuration-processor")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}
