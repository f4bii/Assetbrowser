package de.fabihbbbt.assetbrowser.client.gui.export

import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex
import de.fabihbbbt.assetbrowser.client.gui.index.readBytesOrNull
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.nio.file.Path

class AssetExportActions(
    private val minecraft: Minecraft,
    private val onStatus: (Component) -> Unit,
    private val onCopyFeedback: () -> Unit,
) {
    fun copy(id: Identifier) {
        val target = export(id) ?: return
        onCopyFeedback()
        if (AssetClipboardExporter.copyFilesReferenceToClipboard(listOf(target))) {
            onStatus(Component.literal("Copied ${target.fileName} to clipboard"))
        } else {
            minecraft.keyboardHandler.clipboard = target.toAbsolutePath().toString()
            onStatus(Component.literal("Saved ${target.fileName} — path copied (no file-clipboard tool found)"))
        }
    }

    fun reveal(id: Identifier) {
        val target = export(id) ?: return
        if (AssetClipboardExporter.revealInFileManager(target)) {
            onStatus(Component.literal("Revealed ${target.fileName} in file manager"))
        } else {
            onStatus(Component.literal("Saved to $target (no file manager tool found)"))
        }
    }

    fun copyAll(members: List<AssetIndex.AssetRecord>) {
        val written = ArrayList<Path>()
        for (member in members) {
            val bytes = readResourceBytes(member.id) ?: continue
            val target = AssetClipboardExporter.writeExportFile(member.id, bytes)
            if (target != null) {
                written.add(target)
            }
        }
        if (written.isEmpty()) {
            onStatus(Component.literal("Could not read any files in this group"))
            return
        }

        onCopyFeedback()
        if (AssetClipboardExporter.copyFilesReferenceToClipboard(written)) {
            onStatus(Component.literal("Copied ${written.size} files to clipboard"))
        } else {
            onStatus(Component.literal("Saved ${written.size} files — no file-clipboard tool found"))
        }
    }

    private fun export(id: Identifier): Path? {
        val bytes = readResourceBytes(id)
        if (bytes == null) {
            onStatus(Component.literal("Could not read this resource"))
            return null
        }
        val target = AssetClipboardExporter.writeExportFile(id, bytes)
        if (target == null) {
            onStatus(Component.literal("Could not write exported file"))
        }
        return target
    }

    private fun readResourceBytes(id: Identifier): ByteArray? =
        minecraft.resourceManager
            .getResource(id)
            .orElse(null)
            ?.readBytesOrNull()
}
