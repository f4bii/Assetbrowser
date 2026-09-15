plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
    id("me.modmuss50.mod-publish-plugin") version "2.2.0" apply false
}

stonecutter active "26.2"

stonecutter tasks {
    order("publishModrinth")
}
