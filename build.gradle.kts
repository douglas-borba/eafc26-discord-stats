import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
    id("org.beryx.runtime") version "2.0.1"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
}

group = "com.eafc26"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("com.eafc26.discordstats.Eafc26DiscordStatsApplicationKt")
    applicationName = "EA FC STATS"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.microsoft.playwright:playwright:1.47.0")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testImplementation("org.testcontainers:testcontainers:1.21.0")
    testImplementation("org.testcontainers:postgresql:1.21.0")
    testImplementation("org.testcontainers:junit-jupiter:1.21.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("api.version", "1.43")
    environment("DOCKER_API_VERSION", "1.43")
}

// Load .env.local for local development
fun loadEnvFile(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    
    return file.readLines()
        .filterNot { it.isBlank() || it.trimStart().startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim().removeSurrounding("'").removeSurrounding("\"")
                key to value
            } else null
        }
        .toMap()
}

tasks.named<BootRun>("bootRun") {
    systemProperty("eafc.dashboard.auto-open", "true")
    
    // Load environment variables from .env.local if it exists
    val envFile = project.file(".env.local")
    if (envFile.exists()) {
        val envVars = loadEnvFile(envFile)
        envVars.forEach { (key, value) ->
            environment(key, value)
        }
        println("Loaded ${envVars.size} environment variables from .env.local")
    }
}

tasks.register<BootRun>("dev") {
    group = "application"
    description = "Compatibility alias for the local development flow provided by bootRun."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    systemProperty("eafc.dashboard.auto-open", "true")
    
    // Load environment variables from .env.local if it exists
    val envFile = project.file(".env.local")
    if (envFile.exists()) {
        val envVars = loadEnvFile(envFile)
        envVars.forEach { (key, value) ->
            environment(key, value)
        }
    }
}

runtime {
    options = listOf(
        "--strip-debug",
        "--compress=zip-6",
        "--no-header-files",
        "--no-man-pages",
    )
    modules = listOf(
        "java.base",
        "java.compiler",
        "java.desktop",
        "java.instrument",
        "java.logging",
        "java.management",
        "java.naming",
        "java.net.http",
        "java.prefs",
        "java.security.jgss",
        "java.sql",
        "java.xml",
        "jdk.crypto.ec",
        "jdk.unsupported",
        "jdk.zipfs",
    )

    jpackage {
        outputDir = "macos"
        imageName = "EA FC STATS"
        installerName = "EA FC STATS"
        appVersion = providers.gradleProperty("macAppVersion").getOrElse("1.0.0")
        skipInstaller = true
        jvmArgs = listOf("-Deafc.dashboard.auto-open=true")
        imageOptions = listOf(
            "--mac-package-identifier", "com.eafc26.stats",
            "--mac-package-name", "EA FC STATS",
            "--mac-app-category", "sports",
        )
    }
}

val macAppPath = layout.buildDirectory.dir("macos/EA FC STATS.app")

val verifyMacOs by tasks.registering {
    group = "verification"
    description = "Checks that native macOS packaging is running on macOS."
    doLast {
        require(System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
            "The macOS .app must be generated on macOS."
        }
    }
}

tasks.named("jpackageImage") {
    dependsOn(verifyMacOs)
}

tasks.register("macApp") {
    group = "distribution"
    description = "Generates the self-contained macOS application bundle."
    dependsOn("jpackageImage")

    doLast {
        check(macAppPath.get().asFile.isDirectory) {
            "macOS application bundle was not created at ${macAppPath.get().asFile}"
        }
    }
}

tasks.register<Exec>("openMacApp") {
    group = "application"
    description = "Builds and opens the macOS application bundle."
    dependsOn("macApp")
    commandLine("open", macAppPath.get().asFile.absolutePath)
}

tasks.register("packageApp") {
    group = "application"
    description = "Compatibility task: builds and opens EA FC STATS.app."
    dependsOn("openMacApp")
}

tasks.register("rebuildMacApp") {
    group = "distribution"
    description = "Cleans previous outputs and generates a fresh macOS application bundle."
    dependsOn("clean", "macApp")
}

tasks.named("macApp") {
    mustRunAfter("clean")
}
