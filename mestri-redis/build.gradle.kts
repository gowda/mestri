val testSourceSet = the<SourceSetContainer>()["test"]

dependencies {
  api(project(":mestri-core"))
  api("org.springframework.data:spring-data-redis")
  implementation("io.lettuce:lettuce-core")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  testImplementation(kotlin("test"))
  testImplementation("org.testcontainers:junit-jupiter:1.21.4")
  testImplementation("org.testcontainers:testcontainers:2.0.5")
}

tasks.named<Test>("test") {
  exclude("**/*IntegrationTest.class")
}

tasks.register<Test>("integrationTest") {
  description = "Runs Redis-backed integration tests."
  group = LifecycleBasePlugin.VERIFICATION_GROUP
  testClassesDirs = testSourceSet.output.classesDirs
  classpath = testSourceSet.runtimeClasspath
  include("**/*IntegrationTest.class")
  useJUnitPlatform()
  shouldRunAfter(tasks.named("test"))
}
