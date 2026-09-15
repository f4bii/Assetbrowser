package de.fabihbbbt.assetbrowser.client.gui.preview

import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle
import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle.PANE_BACKGROUND
import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle.PREVIEW_PADDING
import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle.TEXT_MUTED
import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle.TEXT_PRIMARY
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex.AssetRecord
import de.fabihbbbt.assetbrowser.client.gui.sound.AssetSoundPreview
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import kotlin.math.max

class AssetPreviewPane(
    private val minecraft: Minecraft,
    onPlaybackChanged: () -> Unit,
) {
    private val info = ArrayList<String>()
    val file = AssetFilePreview(minecraft)
    val sound = AssetSoundPreview(minecraft, onPlaybackChanged, info::add)

    var record: AssetRecord? = null
        private set

    var x = 0
        private set
    var y = 0
        private set
    var width = 0
        private set
    var height = 0
        private set

    fun setBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
    }

    fun contains(
        mouseX: Double,
        mouseY: Double,
    ): Boolean = AssetBrowserStyle.withinPane(mouseX, mouseY, x, y, width, height)

    fun release() {
        sound.reset()
        file.reset()
    }

    fun open(
        record: AssetRecord,
        index: AssetIndex,
    ): List<Identifier> {
        release()
        this.record = record
        info.clear()
        info.add("Pack: ${record.packId}")

        val stack = minecraft.resourceManager.getResourceStack(record.id)
        if (stack.size > 1) {
            val others =
                stack
                    .map { it.sourcePackId() }
                    .filter { packId -> packId != record.packId }
                    .distinct()
                    .joinToString(", ")
            info.add("Overrides ${stack.size - 1} other pack(s): $others")
        }

        if (record.id.path.endsWith(".ogg")) {
            sound.open(record.id)
            return emptyList()
        }
        val result = file.load(index, record)
        info.addAll(result.infoLines)
        return result.references
    }

    fun readAsText(): List<Identifier> {
        val result = file.readAsText()
        info.addAll(result.infoLines)
        return result.references
    }

    fun tick() {
        file.tick()
        sound.tick()
    }

    fun render(
        graphics: GuiGraphicsExtractor,
        font: Font,
        titleRight: Int,
        mouseX: Int,
        mouseY: Int,
        a: Float,
    ) {
        graphics.fill(x, y, x + width, y + height, PANE_BACKGROUND)

        val record = this.record
        if (record == null) {
            graphics.text(font, "Select a file to preview", x + PREVIEW_PADDING, y + PREVIEW_PADDING, TEXT_MUTED)
            return
        }

        var lineY = y + PREVIEW_PADDING
        val titleWidth = (titleRight - (x + PREVIEW_PADDING)).coerceAtLeast(0)
        graphics.text(font, font.plainSubstrByWidth(record.id.toString(), titleWidth), x + PREVIEW_PADDING, lineY, TEXT_PRIMARY)
        lineY += font.lineHeight + 3

        val infoWidth = (width - PREVIEW_PADDING * 2).coerceAtLeast(0)
        for (line in info) {
            graphics.text(font, font.plainSubstrByWidth(line, infoWidth), x + PREVIEW_PADDING, lineY, TEXT_MUTED)
            lineY += font.lineHeight + 1
        }

        val bodyTop = lineY + 5
        graphics.fill(x + PREVIEW_PADDING, bodyTop - 3, x + width - PREVIEW_PADDING, bodyTop - 2, SEPARATOR)
        val footerY = y + height - PREVIEW_PADDING - font.lineHeight
        val bodyHeight = (footerY - bodyTop - 4).coerceAtLeast(0)

        graphics.enableScissor(x, bodyTop, x + width, max(bodyTop, footerY - 2))
        if (sound.isActive()) {
            val seekHeight = bodyHeight.coerceIn(1, WAVEFORM_HEIGHT)
            val seekSlider = sound.seekSlider
            seekSlider.setRectangle((width - PREVIEW_PADDING * 2).coerceAtLeast(0), seekHeight, x + PREVIEW_PADDING, bodyTop)
            if (!seekSlider.isDragging()) {
                seekSlider.setDisplayedFraction(sound.currentPlaybackFraction())
            }
            seekSlider.extractRenderState(graphics, mouseX, mouseY, a)
        } else {
            file.render(graphics, font, x, width, bodyTop, bodyHeight, footerY)
        }
        graphics.disableScissor()
    }

    companion object {
        private const val SEPARATOR = 0x40FFFFFF
        private const val WAVEFORM_HEIGHT = 40
    }
}
