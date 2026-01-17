import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

group = "com.github.codespaces"
version = "0.1.0"

val pluginId = "com.github.codespaces.toolbox"
val pluginName = "GitHub Codespaces"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/tbx/toolbox-api")
}

dependencies {
    // Toolbox API (compile-only, provided at runtime by Toolbox)
    compileOnly(libs.bundles.toolbox.plugin.api)

    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)

    // JSON parsing
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    // HTTP client (for potential future GitHub API calls)
    implementation(libs.okhttp)

    // Process execution for gh CLI
    implementation(libs.zt.exec)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.bundles.toolbox.plugin.api)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Generate plugin descriptor
tasks.register("generatePluginDescriptor") {
    val outputDir = layout.buildDirectory.dir("pluginDescriptor")
    outputs.dir(outputDir)

    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("extension.json").writeText(
            """
            {
                "id": "$pluginId",
                "name": "$pluginName",
                "version": "$version",
                "meta": {
                    "description": "Connect to GitHub Codespaces from JetBrains Toolbox"
                },
                "compatibleVersionRange": {
                    "from": "2.6.0.0",
                    "to": "2.99.0.0"
                }
            }
            """.trimIndent()
        )
    }
}

// Build plugin distribution
tasks.register<Zip>("buildPlugin") {
    dependsOn("jar", "generatePluginDescriptor")

    archiveFileName.set("$pluginId-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    // Everything goes inside a folder named after the plugin ID
    // Plugin JAR and all dependencies go in lib/
    from(tasks.named("jar")) {
        into("$pluginId/lib")
    }
    from(configurations.runtimeClasspath) {
        into("$pluginId/lib")
    }
    // extension.json in plugin root
    from(layout.buildDirectory.dir("pluginDescriptor")) {
        into(pluginId)
    }
}

// Install plugin to Toolbox plugins directory
tasks.register<Copy>("installPlugin") {
    dependsOn("buildPlugin")

    val userHome = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val pluginDir = when {
        os.contains("mac") -> "$userHome/Library/Application Support/JetBrains/Toolbox/plugins/$pluginId"
        os.contains("linux") -> "$userHome/.local/share/JetBrains/Toolbox/plugins/$pluginId"
        os.contains("windows") -> "${System.getenv("LOCALAPPDATA")}/JetBrains/Toolbox/cache/plugins/$pluginId"
        else -> throw GradleException("Unsupported OS: $os")
    }

    from(zipTree(layout.buildDirectory.file("distributions/$pluginId-$version.zip")))
    into(pluginDir)
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
}
