pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "BlysisConnect"

// Three modules, because a Paper server and a Velocity proxy each hold their own connection
// to the site. The common module holds the protocol client, the journal and every rule; a
// platform module holds only the translation between its platform and a use case.
include(
    ":connect-common",
    ":connect-paper",
    ":connect-velocity",
)
