import java.util.zip.ZipFile

// The Velocity side: the proxy's own connection to the site, its console for the jobs sent
// to the network root, and the commands that make sense on a proxy. It holds no rule of its
// own; the common module decides.

plugins {
    id("uxm.java-conventions")
    alias(libs.plugins.shadow)
}

repositories {
    mavenLocal() // uxmLib during development, before the release reaches repo.uxplima.com
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.uxplima.com/repository/maven-public/") // uxmLib, served anonymously
}

dependencies {
    // Velocity provides the proxy API, Adventure, MiniMessage and Brigadier at runtime.
    compileOnly(libs.velocity.api)

    implementation(project(":connect-common"))

    // uxmlib-common only. uxmlib-command builds a Bukkit command tree, and Velocity has a
    // command manager of its own, so the proxy module registers against that instead.
    implementation(libs.uxmlib.common)
    implementation(libs.configurate.hocon)
    implementation(libs.bstats.velocity)

    testImplementation(libs.velocity.api)
    testImplementation(libs.archunit.junit)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val pluginVersion = version.toString()
    inputs.property("version", pluginVersion)
    // velocity-plugin.json carries one ${version} token, and the same line filter replaces
    // it. Velocity reads this file and nothing else: the proxy never looks at the @Plugin
    // annotation, which only ever fed the annotation processor that writes this file.
    filesMatching("velocity-plugin.json") {
        filter { line -> line.replace("\${version}", pluginVersion) }
    }
}

tasks.shadowJar {
    archiveBaseName.set("BlysisConnect-Velocity")
    archiveClassifier.set("")
    mergeServiceFiles()

    relocate("com.uxplima.uxmlib", "com.uxplima.blysis.libs.uxmlib")
    relocate("com.typesafe.config", "com.uxplima.blysis.libs.typesafe")
    relocate("org.bstats", "com.uxplima.blysis.libs.bstats")
    // Velocity ships Configurate itself, at a version it chose. Relocating our copy keeps
    // the two apart, so a proxy upgrade cannot break this plugin and this plugin cannot
    // pull the proxy onto a different Configurate.
    relocate("org.spongepowered.configurate", "com.uxplima.blysis.libs.configurate")
    // The journal's pool and cache. Another plugin may ship either at another version, so ours carry our
    // namespace. The SQLite driver stays where it is: it finds its native library by its own package.
    relocate("com.zaxxer.hikari", "com.uxplima.blysis.libs.hikari")
    relocate("com.github.benmanes.caffeine", "com.uxplima.blysis.libs.caffeine")
}

tasks.assemble { dependsOn(tasks.shadowJar) }

// Paper owns slf4j, and a second copy of the API with no binding behind it makes logging go
// quiet rather than fail. The other three are annotations that a compiler reads and a runtime
// never loads. geantyref is a real library: Configurate reads a generic type through it, so it
// is relocated rather than dropped.
tasks.shadowJar {
    dependencies {
        exclude(dependency("org.slf4j:.*:.*"))
        exclude(dependency("com.google.errorprone:.*:.*"))
        exclude(dependency("org.checkerframework:.*:.*"))
        exclude(dependency("org.jspecify:.*:.*"))
    }
    relocate("io.leangen.geantyref", "com.uxplima.blysis.libs.geantyref")
}

// Every builder of a plugin audited the jar by hand with unzip, and a hand audit is only correct
// on the day somebody ran it. These are the packages that must never ship inside a plugin jar:
// the server owns them, or they are shaded and must carry our namespace. The SQLite driver is the
// one exception, because the driver derives the path of its native library from its own package.
val forbiddenJarEntries =
    listOf(
        "net/kyori/" to "Paper owns Adventure. A shaded copy breaks the server's own serializers.",
        "org/slf4j/" to "Paper owns slf4j. A second API with no binding makes logging go quiet.",
        "com/uxplima/uxmlib/" to "uxmLib must be relocated. This entry is un-relocated.",
        "org/spongepowered/" to "Configurate must be relocated.",
        "com/typesafe/" to "Typesafe Config must be relocated.",
        "io/leangen/" to "geantyref must be relocated.",
        "com/zaxxer/hikari/" to "HikariCP must be relocated, or another plugin's copy is the one that loads.",
        "com/github/benmanes/caffeine/" to "Caffeine must be relocated, or another plugin's copy is the one that loads.",
        "org/bstats/" to "bStats must be relocated, or two plugins that bundle it clash on the proxy.",
    )

val verifyJar =
    tasks.register("verifyJar") {
        description = "Fail the build when the shaded jar carries a package it must not."
        group = "verification"
        // shadowJar writes over the plain jar, because its classifier is empty, so Gradle sees the
        // file as the output of both tasks and refuses to guess which one has to run first.
        dependsOn(tasks.shadowJar, tasks.jar)
        val jar = tasks.shadowJar.flatMap { shadow -> shadow.archiveFile }
        inputs.file(jar)
        doLast {
            val names = mutableListOf<String>()
            ZipFile(jar.get().asFile).use { archive ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    names.add(entries.nextElement().name)
                }
            }
            val found = mutableListOf<String>()
            for ((prefix, why) in forbiddenJarEntries) {
                var count = 0
                for (name in names) {
                    if (name.startsWith(prefix)) {
                        count++
                    }
                }
                if (count > 0) {
                    found.add("  " + prefix + " (" + count + " entries): " + why)
                }
            }
            if (found.isNotEmpty()) {
                throw GradleException("The jar carries packages it must not:\n" + found.joinToString("\n"))
            }
        }
    }

tasks.check { dependsOn(verifyJar) }
