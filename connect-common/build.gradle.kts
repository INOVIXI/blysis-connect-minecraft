// Everything that is not a platform: the protocol client, the journal, the domain, the use
// cases, the ports and the settings model. An ArchUnit fence proves that no Bukkit type and
// no Velocity type reaches them.

plugins {
    id("uxm.java-conventions")
}

repositories {
    mavenLocal() // uxmLib during development, before the release reaches repo.uxplima.com
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.uxplima.com/repository/maven-public/") // uxmLib
}

dependencies {
    // api, not implementation: HoconConfig, Messages and ConfigurationNode appear in the
    // signatures that a platform module has to name, so they belong on its compile classpath.
    api(libs.uxmlib.common)
    api(libs.configurate.hocon)
    // The journal is a local SQLite file, through the library's pool and migrations.
    api(libs.uxmlib.storage)

    // Gson is compileOnly: Paper and Velocity each ship it, and the protocol is plain JSON.
    compileOnly(libs.gson)
    testImplementation(libs.gson)

    // Adventure is compileOnly on purpose. Paper and Velocity each ship it, and a shaded
    // copy would break the serializers the platform uses on its own components.
    compileOnly(libs.bundles.adventure)

    testImplementation(libs.bundles.adventure)
    testImplementation(libs.archunit.junit)
}
