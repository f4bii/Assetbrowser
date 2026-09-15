package de.fabihbbbt.assetbrowser.client.gui.widget

import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle
import de.fabihbbbt.assetbrowser.client.gui.LoadedAssetsScreen
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import kotlin.math.max

class AssetLinkPanel {
    var x = 0
        private set
    var y = 0
        private set
    var width = 0
        private set
    var height = 0
        private set
    private var items: List<Identifier> = emptyList()
    private var scroll = 0

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

    fun setItems(items: List<Identifier>) {
        this.items = items
    }

    fun resetScroll() {
        scroll = 0
    }

    fun scrollBy(delta: Int): Boolean {
        scroll = (scroll - delta).coerceIn(0, max(0, items.size - 1))
        return true
    }

    fun contains(
        mouseX: Double,
        mouseY: Double,
    ): Boolean = AssetBrowserStyle.withinPane(mouseX, mouseY, x, y, width, height)

    fun render(
        graphics: GuiGraphicsExtractor,
        font: Font,
        title: String,
        scanning: Boolean,
        mouseX: Int,
        mouseY: Int,
    ) {
        graphics.fill(x, y, x + width, y + height, AssetBrowserStyle.PANE_BACKGROUND)
        val heading = "$title (${if (scanning) "…" else items.size.toString()})"
        graphics.text(
            font,
            heading,
            x + AssetBrowserStyle.PREVIEW_PADDING,
            y + AssetBrowserStyle.PREVIEW_PADDING,
            AssetBrowserStyle.TEXT_PRIMARY,
        )

        val bodyTop = bodyTop(font)
        if (scanning) {
            graphics.text(font, "Scanning…", x + AssetBrowserStyle.PREVIEW_PADDING, bodyTop, AssetBrowserStyle.TEXT_MUTED)
            return
        }
        if (items.isEmpty()) {
            graphics.text(font, "(none)", x + AssetBrowserStyle.PREVIEW_PADDING, bodyTop, AssetBrowserStyle.TEXT_MUTED)
            return
        }

        val lineStep = lineStep(font)
        val visibleRows = visibleRows(font)
        val start = firstVisibleRow(font)

        scroll = start
        val textWidth = (width - AssetBrowserStyle.PREVIEW_PADDING * 2).coerceAtLeast(0)

        graphics.enableScissor(x, bodyTop, x + width, y + height)
        var i = 0
        while (i < visibleRows && start + i < items.size) {
            val rowY = bodyTop + i * lineStep
            val hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + lineStep
            val label = font.plainSubstrByWidth(items[start + i].toString(), textWidth)
            graphics.text(
                font,
                label,
                x + AssetBrowserStyle.PREVIEW_PADDING,
                rowY,
                if (hovered) AssetBrowserStyle.TEXT_PRIMARY else AssetBrowserStyle.TEXT_SECONDARY,
            )
            i++
        }
        graphics.disableScissor()
    }

    fun itemAt(
        font: Font,
        mouseX: Double,
        mouseY: Double,
    ): Identifier? {
        val bodyTop = bodyTop(font)
        if (mouseX < x || mouseX >= x + width || mouseY < bodyTop || mouseY >= y + height) {
            return null
        }
        return items.getOrNull(firstVisibleRow(font) + ((mouseY - bodyTop) / lineStep(font)).toInt())
    }

    private fun lineStep(font: Font): Int = font.lineHeight + 2

    private fun bodyTop(font: Font): Int = y + AssetBrowserStyle.PREVIEW_PADDING + font.lineHeight + 4

    private fun visibleRows(font: Font): Int = (y + height - bodyTop(font)) / lineStep(font).coerceAtLeast(0)

    private fun firstVisibleRow(font: Font): Int = (items.size - visibleRows(font)).coerceIn(0, scroll)
}
