package de.fabihbbbt.assetbrowser.client.gui.sound

import it.unimi.dsi.fastutil.floats.FloatArrayList
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.sounds.JOrbisAudioStream
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import java.io.IOException
import java.io.UncheckedIOException
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.roundToInt

class SoundWaveform private constructor(
    val durationSeconds: Float,
    private val peaks: FloatArray,
) {
    fun render(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        playedFraction: Double,
    ) {
        val playHeadX = playheadX(x, w, playedFraction)

        var barX = x
        for (i in 0 until BUCKETS) {
            val barEnd = x + (((i + 1).toLong() * w) / BUCKETS).toInt()
            if (barEnd > barX) {
                val barHeight = (peaks[i] * (h - 2)).coerceAtLeast(1F).roundToInt()
                val barTop = y + (h - barHeight) / 2
                val barRight = if (barEnd - barX > 1) barEnd - 1 else barEnd
                val played = barX < playHeadX
                graphics.fill(barX, barTop, barRight, barTop + barHeight, if (played) PLAYED_COLOR else UNPLAYED_COLOR)
            }
            barX = barEnd
        }
        graphics.fill(playHeadX, y, playHeadX + 1, y + h, PLAYHEAD_COLOR)
    }

    companion object {
        private const val BUCKETS = 240
        private const val PLAYED_COLOR = 0xFF4A90E2.toInt()
        private const val UNPLAYED_COLOR = 0xFF606060.toInt()
        private const val PLAYHEAD_COLOR = 0xFFFFFFFF.toInt()

        fun decode(
            minecraft: Minecraft,
            assetId: Identifier,
        ): CompletableFuture<SoundWaveform> = CompletableFuture.supplyAsync({ decodeSync(minecraft, assetId) }, Util.ioPool())

        private fun decodeSync(
            minecraft: Minecraft,
            assetId: Identifier,
        ): SoundWaveform {
            val resource =
                minecraft.resourceManager.getResource(assetId).orElse(null)
                    ?: throw UncheckedIOException(IOException("Resource no longer available: $assetId"))
            try {
                resource.open().use { input ->
                    JOrbisAudioStream(input).use { stream ->
                        val samples = FloatArrayList(1 shl 16)
                        while (stream.readChunk(samples::add)) { }
                        val channels = stream.format.channels.coerceAtLeast(1)
                        val sampleRate = stream.format.sampleRate
                        val frameCount = samples.size / channels
                        val duration = if (sampleRate > 0F) frameCount / sampleRate else 0F
                        return SoundWaveform(duration, computePeaks(samples, channels, frameCount))
                    }
                }
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }

        private fun computePeaks(
            samples: FloatArrayList,
            channels: Int,
            frameCount: Int,
        ): FloatArray {
            val peaks = FloatArray(BUCKETS)
            if (frameCount <= 0) {
                return peaks
            }
            val raw = samples.elements()
            val size = samples.size
            for (frame in 0 until frameCount) {
                val bucket = ((frame.toLong() * BUCKETS) / frameCount).toInt().coerceAtMost(BUCKETS - 1)
                val base = frame * channels
                var peak = peaks[bucket]
                for (channel in 0 until channels) {
                    val index = base + channel
                    if (index < size) {
                        peak = abs(raw[index]).coerceAtLeast(peak)
                    }
                }
                peaks[bucket] = peak
            }
            var max = 0F
            for (peak in peaks) {
                max = peak.coerceAtLeast(max)
            }
            if (max > 0F) {
                for (i in peaks.indices) {
                    peaks[i] /= max
                }
            }
            return peaks
        }

        fun renderPlaceholder(
            graphics: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            playedFraction: Double,
        ) {
            graphics.fill(x, y + h / 2 - 1, x + w, y + h / 2 + 1, UNPLAYED_COLOR)
            val playheadX = playheadX(x, w, playedFraction)
            graphics.fill(playheadX, y, playheadX + 1, y + h, PLAYHEAD_COLOR)
        }

        private fun playheadX(
            x: Int,
            w: Int,
            playedFraction: Double,
        ): Int = (x + (playedFraction * w).roundToInt()).coerceIn(x, x + w - 1)
    }
}
