package de.fabihbbbt.assetbrowser.client.gui.widget

import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle
import de.fabihbbbt.assetbrowser.client.gui.LoadedAssetsScreen
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex.AssetRecord
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex.Node
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class AssetSidebarList(
    private val screen: LoadedAssetsScreen,
    minecraft: Minecraft,
    width: Int,
    height: Int,
    headerHeight: Int,
) : ObjectSelectionList<AssetSidebarList.Row>(minecraft, width, height, headerHeight, AssetBrowserStyle.ENTRY_HEIGHT) {
    init {
        centerListVertically = false
    }

    override fun getRowWidth(): Int = this.width - 10

    override fun scrollBarX(): Int = right - scrollbarWidth()

    override fun entriesCanBeSelected(): Boolean = false

    fun showTree(root: Node) {
        val scroll = scrollAmount()
        clearEntries()
        for (top in root.sortedChildren) {
            appendVisible(top)
        }
        setScrollAmount(scroll)
    }

    private fun appendVisible(node: Node) {
        if (node.isFile()) {
            addEntry(FileRow(node.record!!, node.name, node.depth))
        } else {
            addEntry(FolderRow(node))
            if (node.expanded) {
                for (child in node.sortedChildren) {
                    appendVisible(child)
                }
            }
        }
    }

    fun showFlat(records: List<AssetRecord>) {
        clearEntries()
        for (record in records) {
            addEntry(FileRow(record, record.id.toString(), 0))
        }
        refreshScrollAmount()
    }

    fun scrollToRecord(id: Identifier) {
        for (row in children()) {
            if (row is FileRow && row.record.id == id) {
                scrollToEntry(row)
                return
            }
        }
    }

    abstract inner class Row(
        protected val depth: Int,
    ) : Entry<Row>() {
        fun textX(): Int = contentX + depth * AssetBrowserStyle.INDENT_PX

        fun textY(): Int = contentY + (contentHeight - this@AssetSidebarList.minecraft.font.lineHeight) / 2
    }

    inner class FolderRow(
        private val node: Node,
    ) : Row(node.depth) {
        override fun extractContent(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            a: Float,
        ) {
            val font = this@AssetSidebarList.minecraft.font
            val color = if (hovered) AssetBrowserStyle.TEXT_PRIMARY else AssetBrowserStyle.TEXT_SECONDARY
            graphics.text(font, if (node.expanded) "-" else "+", textX(), textY(), color)
            val name = font.plainSubstrByWidth(node.name, (contentRight - textX() - 10).coerceAtLeast(0))
            graphics.text(font, name, textX() + 10, textY(), color)
        }

        override fun getNarration(): Component = Component.literal((if (node.expanded) "Collapse " else "Expand ") + node.name)

        override fun mouseClicked(
            event: MouseButtonEvent,
            doubleClick: Boolean,
        ): Boolean {
            node.expanded = !node.expanded
            if (node.isNumberedGroup) {
                this@AssetSidebarList.screen.onGroupSelected(node)
            }
            this@AssetSidebarList.showTree(this@AssetSidebarList.screen.treeRoot)
            return true
        }
    }

    inner class FileRow(
        val record: AssetRecord,
        private val label: String,
        depth: Int,
    ) : Row(depth) {
        override fun extractContent(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            a: Float,
        ) {
            val font = this@AssetSidebarList.minecraft.font
            val selected = record == this@AssetSidebarList.screen.previewRecord
            if (selected) {
                graphics.fill(x, y, x + width, y + height, AssetBrowserStyle.ROW_SELECTED)
            }
            val name = font.plainSubstrByWidth(label, (contentRight - textX()).coerceAtLeast(0))
            graphics.text(
                font,
                name,
                textX(),
                textY(),
                if (selected ||
                    hovered
                ) {
                    AssetBrowserStyle.TEXT_PRIMARY
                } else {
                    AssetBrowserStyle.TEXT_SECONDARY
                },
            )
        }

        override fun getNarration(): Component = Component.literal(record.id.toString())

        override fun mouseClicked(
            event: MouseButtonEvent,
            doubleClick: Boolean,
        ): Boolean {
            this@AssetSidebarList.screen.navigateTo(record.id)
            return true
        }
    }
}
