rootProject.name = "ReactPunishAddon"

include(
    "common"
)

val localPlugin = file("../react-vector")
if (System.getenv("CI") == null && localPlugin.resolve("common").isDirectory) {
    includeBuild(localPlugin) {
        dependencySubstitution {
            substitute(module("com.github.react-minecraft.plugin:react-api")).using(project(":api"))
            substitute(module("com.github.react-minecraft.plugin:react-common")).using(project(":common"))
        }
    }
}

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}