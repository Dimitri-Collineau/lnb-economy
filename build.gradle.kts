plugins {
    `java-library`
    idea
    id("net.neoforged.moddev") version "2.0.148"
    // Must match the Kotlin version bundled by Kotlin for Forge (see kff_version in gradle.properties).
    kotlin("jvm") version "2.4.0"
}

// gradle.properties uses snake_case keys (MDK convention), so map them explicitly.
fun prop(name: String): String = providers.gradleProperty(name).get()

version = prop("mod_version")
group = prop("mod_group_id")

base {
    archivesName = prop("mod_id")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        name = "Kotlin for Forge"
        content { includeGroup("thedarkcolour") }
    }
}

// Other mods' jars live in libs/ and are never committed or redistributed (see README).
val wardenJar = fileTree("libs") { include("lnbwarden*.jar") }
val minevegasJar = fileTree("libs") { include("minevegas*.jar") }

// Tests that call a running LNB site. Excluded from `build`, see README.
val integrationTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

neoForge {
    version = prop("neo_version")

    parchment {
        mappingsVersion = prop("parchment_mappings_version")
        minecraftVersion = prop("parchment_minecraft_version")
    }

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
            programArgument("--nogui")
            // Exports the transformed classes to run/.mixin.out, to check each mixin after a run.
            systemProperty("mixin.debug.export", "true")
        }
        configureEach {
            logLevel = org.slf4j.event.Level.INFO
        }
    }

    // Puts Minecraft and its libraries (Gson, logging...) on the test classpaths.
    addModdingDependenciesTo(sourceSets.test.get())
    addModdingDependenciesTo(integrationTest)

    mods {
        register(prop("mod_id")) {
            sourceSet(sourceSets.main.get())
        }
    }
}

val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath {
    extendsFrom(localRuntime)
}
configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[integrationTest.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${prop("kff_version")}")

    compileOnly(wardenJar)
    localRuntime(wardenJar)
    compileOnly(minevegasJar)
    localRuntime(minevegasJar)
    testImplementation(wardenJar)
    "integrationTestImplementation"(wardenJar)

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "integrationTestImplementation"(kotlin("test-junit5"))
    "integrationTestImplementation"(platform("org.junit:junit-bom:5.11.4"))
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = listOf(
        "minecraft_version",
        "minecraft_version_range",
        "neo_version_range",
        "loader_version_range",
        "kff_version",
        "lnbwarden_version_range",
        "minevegas_version_range",
        "mod_id",
        "mod_name",
        "mod_license",
        "mod_version",
        "mod_authors",
        "mod_description",
    ).associateWith(::prop)
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}
sourceSets.main {
    resources.srcDir(generateModMetadata)
}
neoForge.ideSyncTask(generateModMetadata)

tasks.test {
    useJUnitPlatform()
    workingDir = layout.buildDirectory.dir("test-run").get().asFile.apply { mkdirs() }
}

tasks.register<Test>("integrationTest") {
    description = "Calls a running LNB site. Needs LNB_API_URL and LNB_API_TOKEN."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    workingDir = layout.buildDirectory.dir("test-run").get().asFile.apply { mkdirs() }
    // Always runs against the live site: never reuse a previous result.
    outputs.upToDateWhen { false }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
