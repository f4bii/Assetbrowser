package de.fabihbbbt.assetbrowser.client.gui.widget

import de.fabihbbbt.assetbrowser.client.gui.LoadedAssetsScreenSettings
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max
import kotlin.math.roundToInt

class SplitterLayout(
    private val paneGap: Int,
) {
    var sidebarWidth: Int = -1
    var linksWidth: Int = -1

    var linksVerticalSplit: Double = 0.5

    private var draggingSidebar = false
    private var draggingLinks = false
    private var draggingLinksVertical = false

    fun isDragging(): Boolean = draggingSidebar || draggingLinks || draggingLinksVertical

    fun applySettings(settings: LoadedAssetsScreenSettings) {
        sidebarWidth = settings.sidebarWidth
        linksWidth = settings.linksWidth
        linksVerticalSplit = settings.linksVerticalSplit
    }

    fun toSettings(): LoadedAssetsScreenSettings {
        val settings = LoadedAssetsScreenSettings()
        settings.sidebarWidth = sidebarWidth
        settings.linksWidth = linksWidth
        settings.linksVerticalSplit = linksVerticalSplit
        return settings
    }

    fun layoutWidths(screenWidth: Int) {
        if (sidebarWidth < 0) {
            sidebarWidth = (screenWidth * 0.28).toInt().coerceIn(150, 260)
        }
        if (linksWidth < 0) {
            linksWidth = (screenWidth * 0.26).toInt().coerceIn(140, 240)
        }
        val available = max(MIN_SIDEBAR_WIDTH + MIN_LINKS_WIDTH, screenWidth - paneGap * 2 - MIN_PREVIEW_WIDTH)
        sidebarWidth = sidebarWidth.coerceIn(MIN_SIDEBAR_WIDTH, available - MIN_LINKS_WIDTH)
        linksWidth = linksWidth.coerceIn(MIN_LINKS_WIDTH, available - sidebarWidth)
    }

    fun computeUsedByHeight(linksInnerHeight: Int): Int =
        (linksInnerHeight * linksVerticalSplit).roundToInt().coerceIn(MIN_LINK_PANEL_HEIGHT, linksInnerHeight - MIN_LINK_PANEL_HEIGHT)

    fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        geometry: Geometry,
    ): Boolean {
        if (withinVerticalSplitter(mouseX, mouseY, sidebarWidth, geometry)) {
            draggingSidebar = true
            return true
        }
        if (withinVerticalSplitter(mouseX, mouseY, geometry.previewX + geometry.previewWidth, geometry)) {
            draggingLinks = true
            return true
        }
        if (withinHorizontalSplitter(mouseX, mouseY, geometry)) {
            draggingLinksVertical = true
            return true
        }
        return false
    }

    fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        screenWidth: Int,
        geometry: Geometry,
    ): Boolean {
        if (draggingSidebar) {
            sidebarWidth = mouseX.toInt()
            return true
        }
        if (draggingLinks) {
            linksWidth = (screenWidth - mouseX).toInt()
            return true
        }
        if (draggingLinksVertical) {
            val linksInnerHeight = max(1, geometry.previewHeight - paneGap)
            val fraction = (mouseY - geometry.usedByY) / linksInnerHeight.toDouble()
            linksVerticalSplit = fraction.coerceIn(0.0, 1.0)
            return true
        }
        return false
    }

    fun mouseReleased(): Boolean {
        val wasDragging = isDragging()
        draggingSidebar = false
        draggingLinks = false
        draggingLinksVertical = false
        return wasDragging
    }

    fun render(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        geometry: Geometry,
    ) {
        renderVerticalSplitter(graphics, sidebarWidth, draggingSidebar, mouseX, mouseY, geometry)
        renderVerticalSplitter(graphics, geometry.previewX + geometry.previewWidth, draggingLinks, mouseX, mouseY, geometry)
        renderHorizontalSplitter(graphics, draggingLinksVertical, mouseX, mouseY, geometry)
    }

    private fun withinVerticalSplitter(
        mouseX: Double,
        mouseY: Double,
        gapStartX: Int,
        geometry: Geometry,
    ): Boolean =
        mouseX >= gapStartX - HIT_PADDING && mouseX < gapStartX + paneGap + HIT_PADDING &&
            mouseY >= geometry.previewY && mouseY < geometry.previewY + geometry.previewHeight

    private fun withinHorizontalSplitter(
        mouseX: Double,
        mouseY: Double,
        geometry: Geometry,
    ): Boolean {
        val gapStartY = geometry.usedByY + geometry.usedByHeight
        return mouseY >= gapStartY - HIT_PADDING && mouseY < gapStartY + paneGap + HIT_PADDING &&
            mouseX >= geometry.usedByX && mouseX < geometry.usedByX + geometry.usedByWidth
    }

    private fun renderVerticalSplitter(
        graphics: GuiGraphicsExtractor,
        gapStartX: Int,
        forceHighlight: Boolean,
        mouseX: Int,
        mouseY: Int,
        geometry: Geometry,
    ) {
        val highlighted = forceHighlight || withinVerticalSplitter(mouseX.toDouble(), mouseY.toDouble(), gapStartX, geometry)
        val lineX = gapStartX + paneGap / 2
        graphics.fill(
            lineX,
            geometry.previewY,
            lineX + 1,
            geometry.previewY + geometry.previewHeight,
            if (highlighted) HOVER_COLOR else COLOR,
        )
    }

    private fun renderHorizontalSplitter(
        graphics: GuiGraphicsExtractor,
        forceHighlight: Boolean,
        mouseX: Int,
        mouseY: Int,
        geometry: Geometry,
    ) {
        val highlighted = forceHighlight || withinHorizontalSplitter(mouseX.toDouble(), mouseY.toDouble(), geometry)
        val lineY = geometry.usedByY + geometry.usedByHeight + paneGap / 2
        graphics.fill(geometry.usedByX, lineY, geometry.usedByX + geometry.usedByWidth, lineY + 1, if (highlighted) HOVER_COLOR else COLOR)
    }

    companion object {
        private const val HIT_PADDING = 3
        private const val COLOR = 0x50FFFFFFL.toInt()
        private const val HOVER_COLOR = 0xB0FFFFFFL.toInt()
        private const val MIN_SIDEBAR_WIDTH = 120
        private const val MIN_LINKS_WIDTH = 120
        private const val MIN_PREVIEW_WIDTH = 200
        private const val MIN_LINK_PANEL_HEIGHT = 30
    }

    data class Geometry(
        val previewX: Int,
        val previewY: Int,
        val previewWidth: Int,
        val previewHeight: Int,
        val usedByX: Int,
        val usedByY: Int,
        val usedByWidth: Int,
        val usedByHeight: Int,
    )
}
