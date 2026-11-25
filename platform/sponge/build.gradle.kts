import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion

repositories {
    maven("https://repo.spongepowered.org/maven/")
}

dependencies {
    api(project(":common"))
    compileOnly(libs.sponge.api)
    compileOnly(libs.react.api)
    compileOnly("org.jetbrains:annotations:26.0.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

configurations.configureEach {
    if (name == "compileClasspath" || name.endsWith("CompileClasspath")) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("punish-addon-sponge-${project.version}.jar")
}
