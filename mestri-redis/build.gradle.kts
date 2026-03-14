dependencies {
  api(project(":mestri-core"))
  api("org.springframework.data:spring-data-redis")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  testImplementation(kotlin("test"))
}
