dependencies {
  api(project(":mestri-core"))
  api("org.springframework.data:spring-data-redis")
  implementation("io.lettuce:lettuce-core")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  testImplementation(kotlin("test"))
  testImplementation("org.testcontainers:junit-jupiter:1.21.4")
  testImplementation("org.testcontainers:testcontainers:1.21.4")
}
