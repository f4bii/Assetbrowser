package de.fabihbbbt.assetbrowser.client.gui

object AssetBrowserStyle {

    const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xFFC8C8C8.toInt()
    const val TEXT_MUTED = 0xFF909090.toInt()
    const val TEXT_ERROR = 0xFFFF7070.toInt()
    const val ROW_SELECTED = 0x554A90E2
    const val PANE_BACKGROUND = 0x40000000
    const val DROPDOWN_BACKGROUND = 0xF0101010.toInt()
    const val DROPDOWN_BORDER = 0xFF3F3F3F.toInt()

    const val ENTRY_HEIGHT = 14
    const val INDENT_PX = 10
    const val PREVIEW_PADDING = 8
    const val DROPDOWN_MAX_VISIBLE_ROWS = 8

    fun withinPane(
        mouseX: Double,
        mouseY: Double,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ): Boolean = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
}
