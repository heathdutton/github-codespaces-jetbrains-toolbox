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

    // Kotlin (provided by Toolbox at runtime)
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlinx.coroutines.core)

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

// Exclude Kotlin stdlib from all runtime dependencies (Toolbox provides it)
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
    exclude(group = "org.jetbrains", module = "annotations")
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
                "version": "$version",
                "apiVersion": "${libs.versions.toolbox.api.get()}",
                "meta": {
                    "readableName": "$pluginName",
                    "description": "Connect to GitHub Codespaces from JetBrains Toolbox",
                    "vendor": "heathdutton",
                    "url": "https://github.com/heathdutton/github-codespaces-jetbrains-toolbox"
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
    // JARs go directly in plugin root (not in lib/)
    from(tasks.named("jar")) {
        into(pluginId)
    }
    from(configurations.runtimeClasspath) {
        into(pluginId)
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
        os.contains("mac") -> "$userHome/Library/Caches/JetBrains/Toolbox/plugins/$pluginId"
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
