pluginManagement {

    repositories {

        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()

    }

}

rootProject.name = "ReactPunishAddon"

include(
    "common",
    "platform:bukkit",
    "platform:fabric",
    "platform:minestom",
    "platform:sponge"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
