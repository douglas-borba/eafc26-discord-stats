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

tasks.register("macApp") {
    group = "distribution"
    description = "Generates the self-contained macOS application bundle with Swift launcher."
    dependsOn("bootJar", verifyMacOs)

    doLast {
        val projectDir = project.projectDir
        val buildDir = project.layout.buildDirectory.get().asFile
        val appBundle = File(buildDir, "macos/EA FC STATS.app")
        val contentsDir = File(appBundle, "Contents")
        val macOSDir = File(contentsDir, "MacOS")
        val appDir = File(contentsDir, "app")
        val resourcesDir = File(contentsDir, "Resources")
        
        println("=== Building EA FC STATS.app with Swift launcher ===")
        
        // Clean previous build
        if (appBundle.exists()) {
            appBundle.deleteRecursively()
        }
        
        // Create bundle structure
        macOSDir.mkdirs()
        appDir.mkdirs()
        resourcesDir.mkdirs()
        
        // Compile Swift launcher
        println("Compiling Swift launcher...")
        val swiftSource = File(projectDir, "launcher/EAFCStatsLauncher.swift")
        val swiftBinary = File(macOSDir, "EA FC Stats")
        
        val swiftCompile = ProcessBuilder(
            "swiftc",
            "-parse-as-library",
            "-framework", "AppKit",
            "-framework", "SwiftUI",
            "-O",
            "-o", swiftBinary.absolutePath,
            swiftSource.absolutePath
        ).redirectErrorStream(true).start()
        
        val swiftOutput = swiftCompile.inputStream.bufferedReader().readText()
        val swiftExitCode = swiftCompile.waitFor()
        
        if (swiftExitCode != 0) {
            throw GradleException("Swift compilation failed:\n$swiftOutput")
        }
        
        swiftBinary.setExecutable(true)
        println("✓ Swift launcher compiled")
        
        // Copy JAR
        val libsDir = File(buildDir, "libs")
        val jarFile = libsDir.listFiles()?.firstOrNull { 
            it.name.endsWith(".jar") && !it.name.contains("-plain") 
        } ?: throw GradleException("Boot JAR not found in build/libs")
        
        val destJar = File(appDir, jarFile.name)
        jarFile.copyTo(destJar, overwrite = true)
        println("✓ Copied JAR: ${jarFile.name}")
        
        // Create Info.plist
        val infoPlist = File(contentsDir, "Info.plist")
        infoPlist.writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>CFBundleName</key>
              <string>EA FC Stats</string>
              <key>CFBundleDisplayName</key>
              <string>EA FC Stats</string>
              <key>CFBundleIdentifier</key>
              <string>com.eafc26.stats.launcher</string>
              <key>CFBundleVersion</key>
              <string>1.0.0</string>
              <key>CFBundleShortVersionString</key>
              <string>1.0.0</string>
              <key>CFBundleExecutable</key>
              <string>EA FC Stats</string>
              <key>CFBundlePackageType</key>
              <string>APPL</string>
              <key>CFBundleIconFile</key>
              <string>AppIcon</string>
              <key>LSMinimumSystemVersion</key>
              <string>13.0</string>
              <key>NSHighResolutionCapable</key>
              <true/>
              <key>NSQuitAlwaysKeepsWindows</key>
              <false/>
            </dict>
            </plist>
        """.trimIndent())
        println("✓ Created Info.plist")
        
        // Validate Info.plist
        val validatePlist = ProcessBuilder("plutil", "-lint", infoPlist.absolutePath)
            .redirectErrorStream(true).start()
        validatePlist.waitFor()
        
        // Create .env.example (NOT .env.local with real credentials)
        val envExample = File(projectDir, ".env.example")
        if (envExample.exists()) {
            val destEnvExample = File(appDir, ".env.example")
            envExample.copyTo(destEnvExample, overwrite = true)
            println("✓ Copied .env.example as configuration template")
        }
        
        println("\n=== Bundle structure ===")
        println("Contents/MacOS/")
        macOSDir.listFiles()?.forEach { file ->
            val perm = if (file.canExecute()) "x" else "-"
            println("  [$perm] ${file.name}")
        }
        println("Contents/app/")
        appDir.listFiles()?.forEach { file ->
            println("  [+] ${file.name}")
        }
        
        println("\n✓ EA FC STATS.app built successfully")
        println("  Location: ${appBundle.relativeTo(projectDir)}")
        println("  Launcher: Swift native")
        println("  Configuration: ~/Library/Application Support/EAFC26DiscordStats/.env.local")
        println("  Security: No credentials in bundle")
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

