// ManulLibs Map — клиентская привязка карты Conflux Map (форк в ../) к серверу ManulCraft.
// GPL-3.0-or-later, как и сама карта.
plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17-SNAPSHOT"
    kotlin("jvm") version "2.0.21"
}

val minecraftVersion = project.property("minecraft_version") as String
version = project.property("mod_version") as String
group = project.property("maven_group") as String
base { archivesName.set("manullibs-map") }

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    // Сюда кладётся собранный форк (см. README): libs/repo/ru/manulcraft/confluxmap-manul/<ver>/
    maven(uri("libs/repo")) {
        name = "Local"
        content { includeGroup("ru.manulcraft") }
    }
}

val confluxMap = project.property("conflux_map") as String

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")

    // Карта вкладывается в этот мод (jar-in-jar); API — только для компиляции.
    modImplementation(confluxMap)
    include(confluxMap)
    compileOnly(files("libs/confluxmap-api.jar"))
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
}

kotlin { jvmToolchain(21) }

tasks.jar { from("LICENSE") }

tasks.remapJar {
    archiveVersion.set("${project.version}+$minecraftVersion")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand(mapOf("version" to project.version)) }
}
