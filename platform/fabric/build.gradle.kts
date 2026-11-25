import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    alias(libs.plugins.fabric.loom)
}

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    api(project(":common"))
    compileOnly(libs.react.api)
    compileOnly(libs.adventure.api)
    compileOnly("org.jetbrains:annotations:26.0.2")
}

val addonRuntime = configurations.create("addonRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    add(addonRuntime.name, project(":common"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

configurations.configureEach {
    if (name == "compileClasspath" || name.endsWith("CompileClasspath")) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

tasks.named<ProcessResources>("processResources") {
    val metadata = mapOf("version" to project.version)
    inputs.properties(metadata)
    filesMatching("fabric.mod.json") {
        expand(metadata)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("punish-addon-fabric-${project.version}.jar")
    configurations = listOf(addonRuntime)
}
