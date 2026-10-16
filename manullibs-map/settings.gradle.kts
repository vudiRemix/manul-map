pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
    // Тот же Loom, что у форка (../): вложенный jar карты собран им.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "net.fabricmc.fabric-loom-remap") {
                useModule("net.fabricmc:fabric-loom:${requested.version}")
            }
        }
    }
}

rootProject.name = "manullibs-map"
