plugins {
    id("net.minecraftforge.gradle") version "6.0.24"
    id("org.parchmentmc.librarian.forgegradle") version "1.+"
    id("io.github.goooler.shadow")
}

val minecraftVersion = "1.20.1"
val forgeVersion = "47.2.0"

minecraft {
    mappings("parchment", "2023.09.03-1.20.1")

    copyIdeResources.set(true)

    runs {
        create("server") {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            args("--nogui")
        }
    }
}

dependencies {
    "minecraft"("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")
    implementation(project(":Freesia-Common"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to "freesia-backend-forge",
                "Specification-Vendor" to "nguyendevs",
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "nguyendevs",
                "Implementation-Timestamp" to System.currentTimeMillis().toString()
            )
        )
    }
}
