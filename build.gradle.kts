import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    application
    kotlin("jvm") version "1.6.20"
}

group = "tsb99x"
version = gitVersion()

val vertxVersion = "4.3.7"
val awsSdkVersion = "2.19.5"
val kotlinxCoroutinesVersion = "1.5.2"
val logbackVersion = "1.4.5"
val testContainersVersion = "1.17.6"

val mainVerticleName = "tsb99x.kinescope.Application"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

val imageTag = "ghcr.io/tsb99x/kinescope:${project.version}"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:$kotlinxCoroutinesVersion"))
    implementation(platform("org.testcontainers:testcontainers-bom:$testContainersVersion"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")

    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")

    implementation("software.amazon.awssdk:kinesis")

    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation("io.vertx:vertx-junit5")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
}

application {
    mainClass.set(launcherClassName)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf("Implementation-Version" to project.version))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events = setOf(PASSED, SKIPPED, FAILED)
    }
}

tasks.withType<JavaExec> {
    args = listOf(
        "run",
        mainVerticleName,
        "--redeploy=$watchForChange",
        "--launcher-class=$launcherClassName",
        "--on-redeploy=$doOnChange"
    )
}

tasks.register<Exec>("dockerBuild") {
    group = "docker"
    dependsOn("build")
    commandLine("docker", "build", "--build-arg", "VERSION=${project.version}", "-t", imageTag, ".")
}

tasks.register<Exec>("dockerPush") {
    group = "docker"
    commandLine("docker", "push", imageTag)
}

fun gitVersion(): String {
    ByteArrayOutputStream().use { output ->
        exec {
            commandLine("git", "describe", "--always", "--first-parent", "--dirty")
            standardOutput = output
        }
        return output.toString()
            .replace("\n", "")
            .replace("\r", "")
    }
}
