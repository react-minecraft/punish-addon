rootProject.name = "ReactPunishAddon"

include(
    "common"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}