import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

dependencies {
    api(project(":common"))
    compileOnly(libs.paper.api)
    compileOnly(libs.react.api)
    compileOnly("org.jetbrains:annotations:26.0.2")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("punish-addon-bukkit-${project.version}.jar")
}
