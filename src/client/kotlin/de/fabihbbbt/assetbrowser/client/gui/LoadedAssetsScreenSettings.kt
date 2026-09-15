package de.fabihbbbt.assetbrowser.client.gui

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class LoadedAssetsScreenSettings {
    var sidebarWidth: Int = -1
    var linksWidth: Int = -1
    var linksVerticalSplit: Double = 0.5

    fun save() {
        runCatching {
            Files.createDirectories(SETTINGS_FILE.parent)
            Files.writeString(SETTINGS_FILE, GSON.toJson(this), StandardCharsets.UTF_8)
        }
    }

    private fun sanitized(): LoadedAssetsScreenSettings {
        if (sidebarWidth <= 0) {
            sidebarWidth = -1
        }
        if (linksWidth <= 0) {
            linksWidth = -1
        }
        if (linksVerticalSplit !in 0.0..1.0) {
            linksVerticalSplit = 0.5
        }
        return this
    }

    companion object {
        private val SETTINGS_FILE: Path by lazy {
            FabricLoader
                .getInstance()
                .configDir
                .resolve("assetbrowser")
                .resolve("loaded-assets-screen.json")
        }
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(): LoadedAssetsScreenSettings =
            runCatching {
                Files.newBufferedReader(SETTINGS_FILE, StandardCharsets.UTF_8).use { reader ->
                    GSON.fromJson(reader, LoadedAssetsScreenSettings::class.java)?.sanitized()
                }
            }.getOrNull() ?: LoadedAssetsScreenSettings()
    }
}
