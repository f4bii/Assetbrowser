package de.fabihbbbt.assetbrowser.client.gui.widget

import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import kotlin.math.min

class AssetFilterDropdown(
    private val labelPrefix: String,
    width: Int,
    private val options: List<String>,
    initialSelection: String,
    private val onSelect: (String) -> Unit,
) {
    val button: Button
    var selected: String
        private set
    private var open = false
    private var scroll = 0

    init {
        selected = if (options.contains(initialSelection)) initialSelection else options[0]
        button = Button.builder(Component.literal(labelPrefix + selected)) { toggle() }.size(width, 20).build()
    }

    fun select(option: String) {
        open = false
        if (option == selected) {
            return
        }
        selected = option
        button.setMessage(Component.literal(labelPrefix + option))
        onSelect(option)
    }

    private fun toggle() {
        open = !open
        scroll = 0
    }

    private fun visibleRows(): Int = min(AssetBrowserStyle.DROPDOWN_MAX_VISIBLE_ROWS, options.size)

    private fun maxScroll(): Int = (options.size - visibleRows()).coerceAtLeast(0)

    private fun firstVisibleRow(): Int = min(scroll, maxScroll())

    private fun listTop(): Int = button.y + button.getHeight()

    private fun isOverList(
        mouseX: Double,
        mouseY: Double,
    ): Boolean =
        AssetBrowserStyle.withinPane(
            mouseX,
            mouseY,
            button.x,
            listTop(),
            button.getWidth(),
            visibleRows() * AssetBrowserStyle.ENTRY_HEIGHT,
        )

    private fun rowAt(
        mouseX: Double,
        mouseY: Double,
    ): Int {
        if (!isOverList(mouseX, mouseY)) {
            return -1
        }
        val row = firstVisibleRow() + ((mouseY - listTop()) / AssetBrowserStyle.ENTRY_HEIGHT).toInt()
        return if (row < options.size) row else -1
    }

    fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
    ): Boolean {
        if (!open) {
            return false
        }
        val row = rowAt(mouseX, mouseY)
        if (row >= 0) {
            select(options[row])
            return true
        }
        if (!button.isMouseOver(mouseX, mouseY)) {
            open = false
        }
        return false
    }

    fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        delta: Int,
    ): Boolean {
        if (!open || !isOverList(mouseX, mouseY)) {
            return false
        }
        scroll = (scroll - delta).coerceIn(0, maxScroll())
        return true
    }

    fun render(
        graphics: GuiGraphicsExtractor,
        font: Font,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (!open) {
            return
        }
        val x = button.x
        val y = listTop()
        val w = button.getWidth()
        val visibleRows = visibleRows()
        val h = visibleRows * AssetBrowserStyle.ENTRY_HEIGHT
        val start = firstVisibleRow()

        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, AssetBrowserStyle.DROPDOWN_BORDER)
        graphics.fill(x, y, x + w, y + h, AssetBrowserStyle.DROPDOWN_BACKGROUND)

        val textWidth = w - AssetBrowserStyle.PREVIEW_PADDING.coerceAtLeast(0)
        var i = 0
        while (i < visibleRows && start + i < options.size) {
            val option = options[start + i]
            val rowY = y + i * AssetBrowserStyle.ENTRY_HEIGHT
            val hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + AssetBrowserStyle.ENTRY_HEIGHT
            val isSelected = option == selected
            if (hovered) {
                graphics.fill(x, rowY, x + w, rowY + AssetBrowserStyle.ENTRY_HEIGHT, AssetBrowserStyle.ROW_SELECTED)
            }
            val label = font.plainSubstrByWidth(option, textWidth)
            graphics.text(
                font,
                label,
                x + 4,
                rowY + (AssetBrowserStyle.ENTRY_HEIGHT - font.lineHeight) / 2,
                if (isSelected) AssetBrowserStyle.TEXT_PRIMARY else AssetBrowserStyle.TEXT_SECONDARY,
            )
            i++
        }
    }
}
