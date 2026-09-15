package de.fabihbbbt.assetbrowser.client.gui.preview

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import de.fabihbbbt.assetbrowser.client.gui.AssetBrowserStyle
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex
import de.fabihbbbt.assetbrowser.client.gui.index.AssetIndex.AssetRecord
import de.fabihbbbt.assetbrowser.client.gui.index.readBytesOrNull
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.metadata.animation.AnimationFrame
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.jvm.optionals.getOrNull
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

class AssetFilePreview(
    private val minecraft: Minecraft,
) {
    private var hasImage = false
    private var imageWidth = 0
    private var imageHeight = 0
    private var animation: AssetAnimation? = null
    private var animationFrameCursor = 0
    private var animationTicksRemaining = 0
    private var lines: List<String>? = null
    private var scroll = 0
    private var message = NO_PREVIEW_MESSAGE

    private var pendingText: PendingText? = null

    private class PendingText(
        val id: Identifier,
        val bytes: ByteArray,
        val index: AssetIndex,
    )

    data class LoadResult(
        val infoLines: List<String>,
        val references: List<Identifier>,
    )

    fun reset() {
        minecraft.textureManager.release(PREVIEW_TEXTURE_ID)
        hasImage = false
        animation = null
        lines = null
        scroll = 0
        message = NO_PREVIEW_MESSAGE
        pendingText = null
    }

    fun tick() {
        val anim = animation
        if (anim != null && anim.frameCount() > 1 && --animationTicksRemaining <= 0) {
            animationFrameCursor = (animationFrameCursor + 1) % anim.frameCount()
            animationTicksRemaining = anim.frameTicks[animationFrameCursor]
        }
    }

    fun scroll(delta: Int): Boolean {
        val currentLines = lines ?: return false
        scroll = (scroll - delta).coerceIn(0, (currentLines.size - 1).coerceAtLeast(0))
        return true
    }

    fun load(
        index: AssetIndex,
        record: AssetRecord,
    ): LoadResult {
        val infoLines = ArrayList<String>()
        val resource = minecraft.resourceManager.getResource(record.id).orElse(null)
        if (resource == null) {
            message = "Resource no longer available"
            return LoadResult(infoLines, emptyList())
        }
        val bytes = resource.readBytesOrNull()
        if (bytes == null) {
            message = "Could not read this resource"
            return LoadResult(infoLines, emptyList())
        }
        val path = record.id.path

        infoLines.add("Size: " + formatSize(bytes.size))
        if (bytes.size > MAX_PREVIEW_BYTES) {
            message = "File too large to preview"
            return LoadResult(infoLines, emptyList())
        }
        if (path.endsWith(".png")) {
            loadImage(record, resource, bytes, infoLines)
            return LoadResult(infoLines, emptyList())
        }
        if (AssetIndex.isTextAsset(path)) {
            val references = loadText(index, record.id, bytes, infoLines)
            return LoadResult(infoLines, references)
        }
        pendingText = PendingText(record.id, bytes, index)
        return LoadResult(infoLines, emptyList())
    }

    fun canReadAsText(): Boolean = pendingText != null

    fun readAsText(): LoadResult {
        val pending = pendingText ?: return LoadResult(emptyList(), emptyList())
        val infoLines = ArrayList<String>()
        val references = loadText(pending.index, pending.id, pending.bytes, infoLines)
        pendingText = null
        return LoadResult(infoLines, references)
    }

    private fun loadImage(
        record: AssetRecord,
        resource: Resource,
        bytes: ByteArray,
        infoLines: MutableList<String>,
    ) {
        runCatching {
            val image = NativeImage.read(bytes)
            minecraft.textureManager.register(PREVIEW_TEXTURE_ID, DynamicTexture({ record.id.toString() }, image))
            hasImage = true
            imageWidth = image.width
            imageHeight = image.height
            infoLines.add("Image: ${image.width} x ${image.height} px")
            val anim = AssetAnimation.from(resource, image.width, image.height)
            animation = anim
            if (anim != null) {
                animationFrameCursor = 0
                animationTicksRemaining = anim.frameTicks[0]
                infoLines.add("Animated: ${anim.frameCount()} frame(s)")
            }
        }.onFailure { message = "Could not decode image" }
    }

    private fun loadText(
        index: AssetIndex,
        id: Identifier,
        bytes: ByteArray,
        infoLines: MutableList<String>,
    ): List<Identifier> {
        val raw = String(bytes, StandardCharsets.UTF_8)
        val pretty = prettyPrintIfJson(id.path, raw)
        val textLines = ArrayList<String>()
        for (line in pretty.display.split("\n")) {
            if (textLines.size >= MAX_PREVIEW_LINES) {
                textLines.add("... truncated at $MAX_PREVIEW_LINES lines")
                break
            }
            textLines.add(line.replace("\r", "").replace("\t", "    "))
        }
        lines = textLines
        infoLines.add("Text: ${textLines.size} line(s)")
        val parsed = pretty.parsed
        if (id.path.startsWith("blockstates/") && parsed != null) {
            val blockstateSummary = AssetIndex.summarizeBlockstate(parsed)
            if (blockstateSummary != null) {
                infoLines.add("Blockstate: $blockstateSummary")
            }
        }
        return index.referencesIn(id, raw)
    }

    private data class JsonPrettyResult(
        val display: String,
        val parsed: JsonElement?,
    )

    fun render(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        w: Int,
        bodyTop: Int,
        bodyHeight: Int,
        footerY: Int,
    ) {
        if (hasImage) {
            renderImageBody(graphics, font, x, w, bodyTop, bodyHeight, footerY)
        } else if (lines != null) {
            renderTextBody(graphics, font, x, w, bodyTop, bodyHeight, footerY)
        } else {
            graphics.text(font, message, x + AssetBrowserStyle.PREVIEW_PADDING, bodyTop, AssetBrowserStyle.TEXT_ERROR)
        }
    }

    private fun renderImageBody(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        w: Int,
        bodyTop: Int,
        bodyHeight: Int,
        footerY: Int,
    ) {
        val anim = animation
        val frameW = anim?.frameWidth ?: imageWidth
        val frameH = anim?.frameHeight ?: imageHeight
        val maxW = (w - AssetBrowserStyle.PREVIEW_PADDING * 2).coerceAtLeast(1)
        val maxH = bodyHeight.coerceAtLeast(1)
        var scale = min(maxW.toFloat() / frameW, maxH.toFloat() / frameH)
        if (scale >= 1.0F) {
            scale = floor(scale.toDouble()).toFloat()
        }
        val drawW = (frameW * scale).coerceAtLeast(1F).roundToInt()
        val drawH = (frameH * scale).coerceAtLeast(1F).roundToInt()
        val imgX = x + (w - drawW) / 2
        val imgY = bodyTop + ((maxH - drawH) / 2).coerceAtLeast(0)

        var u = 0.0F
        var v = 0.0F
        if (anim != null) {
            val frameIndex = anim.frameIndices[animationFrameCursor]
            u = ((frameIndex % anim.columns) * frameW).toFloat()
            v = ((frameIndex / anim.columns) * frameH).toFloat()
        }

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            PREVIEW_TEXTURE_ID,
            imgX,
            imgY,
            u,
            v,
            drawW,
            drawH,
            frameW,
            frameH,
            imageWidth,
            imageHeight,
        )

        var label = "Scaled x" + trimScale(scale)
        if (anim != null) {
            label += " · frame ${animationFrameCursor + 1}/${anim.frameCount()}"
        }
        graphics.text(font, label, x + AssetBrowserStyle.PREVIEW_PADDING, footerY, AssetBrowserStyle.TEXT_MUTED)
    }

    private fun renderTextBody(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        w: Int,
        bodyTop: Int,
        bodyHeight: Int,
        footerY: Int,
    ) {
        val currentLines = lines ?: return
        val lineStep = font.lineHeight + 1
        val visibleLines = (bodyHeight / lineStep).coerceAtLeast(1)
        val maxScroll = (currentLines.size - visibleLines).coerceAtLeast(0)
        val start = min(scroll, maxScroll)

        scroll = start
        val textWidth = (w - AssetBrowserStyle.PREVIEW_PADDING * 2).coerceAtLeast(0)

        var i = 0
        while (i < visibleLines && start + i < currentLines.size) {
            val line = font.plainSubstrByWidth(currentLines[start + i], textWidth)
            graphics.text(font, line, x + AssetBrowserStyle.PREVIEW_PADDING, bodyTop + i * lineStep, AssetBrowserStyle.TEXT_SECONDARY)
            i++
        }

        val shownTo = min(currentLines.size, start + visibleLines)
        val footer =
            "Lines ${start + 1}-$shownTo of ${currentLines.size}" +
                (if (maxScroll > 0) " (scroll to see more)" else "")
        graphics.text(font, footer, x + AssetBrowserStyle.PREVIEW_PADDING, footerY, AssetBrowserStyle.TEXT_MUTED)
    }

    private class AssetAnimation private constructor(
        val frameWidth: Int,
        val frameHeight: Int,
        val columns: Int,
        val frameIndices: IntArray,
        val frameTicks: IntArray,
    ) {
        fun frameCount(): Int = frameIndices.size

        companion object {
            fun from(
                resource: Resource,
                imageWidth: Int,
                imageHeight: Int,
            ): AssetAnimation? {
                val meta =
                    runCatching {
                        resource.metadata().getSection(AnimationMetadataSection.TYPE).getOrNull()
                    }.getOrNull() ?: return null

                val size = meta.calculateFrameSize(imageWidth, imageHeight)
                val frameWidth = size.width().coerceAtLeast(1)
                val frameHeight = size.height().coerceAtLeast(1)
                val columns = (imageWidth / frameWidth).coerceAtLeast(1)
                val rows = (imageHeight / frameHeight).coerceAtLeast(1)
                val totalFrames = columns * rows
                if (totalFrames <= 1) {
                    return null
                }

                val explicit: List<AnimationFrame>? = meta.frames().orElse(null)
                val frameCount = explicit?.size ?: totalFrames
                if (frameCount == 0) {
                    return null
                }
                val indices = IntArray(frameCount) { i -> explicit?.get(i)?.index() ?: i }
                val ticks =
                    IntArray(frameCount) { i ->
                        (explicit?.get(i)?.timeOr(meta.defaultFrameTime()) ?: meta.defaultFrameTime()).coerceAtLeast(1)
                    }
                return AssetAnimation(frameWidth, frameHeight, columns, indices, ticks)
            }
        }
    }

    companion object {
        private val PREVIEW_TEXTURE_ID: Identifier = Identifier.fromNamespaceAndPath("assetbrowser", "asset_preview")
        private val PRETTY_GSON: Gson = GsonBuilder().setPrettyPrinting().create()
        private const val NO_PREVIEW_MESSAGE = "No preview available for this file type"
        private const val MAX_PREVIEW_BYTES = 8 * 1024 * 1024
        private const val MAX_PREVIEW_LINES = 5000

        private fun prettyPrintIfJson(
            path: String,
            raw: String,
        ): JsonPrettyResult {
            if (!path.endsWith(".json") && !path.endsWith(".mcmeta")) {
                return JsonPrettyResult(raw, null)
            }
            val element =
                runCatching { JsonParser.parseString(raw) }.getOrNull()
                    ?: return JsonPrettyResult(raw, null)
            return JsonPrettyResult(PRETTY_GSON.toJson(element), element)
        }

        private fun formatSize(bytes: Int): String {
            if (bytes < 1024) {
                return "$bytes B"
            }
            if (bytes < 1024 * 1024) {
                return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
            }
            return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0))
        }

        private fun trimScale(scale: Float): String =
            if (scale == floor(scale.toDouble()).toFloat()) scale.toInt().toString() else String.format(Locale.ROOT, "%.2f", scale)
    }
}
