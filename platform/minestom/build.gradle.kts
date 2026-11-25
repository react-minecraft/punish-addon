import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion

dependencies {
    api(project(":common"))
    compileOnly(libs.minestom)
    compileOnly(libs.react.api)
    compileOnly(libs.actions)
    compileOnly(libs.schedula.common)
    compileOnly(libs.configurate.yaml)
}

configurations.compileClasspath {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("punish-addon-minestom-${project.version}.jar")
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
