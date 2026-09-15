package de.fabihbbbt.assetbrowser.client.gui.sound

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.util.*
import kotlin.math.roundToInt

class AssetSoundPreview(
    private val minecraft: Minecraft,
    private val onPlaybackChanged: () -> Unit,
    private val onInfoLine: (String) -> Unit,
) {
    val seekSlider = SoundSeekSlider(this)

    private var assetId: Identifier? = null
    private var currentSound: AssetSoundInstance? = null
    var waveform: SoundWaveform? = null
        private set
    private var loadToken = 0
    private var playStartMillis = 0L
    private var pendingSeekSeconds = 0F
    private var pendingSeekTicksRemaining = 0

    fun isActive(): Boolean = assetId != null

    fun open(assetId: Identifier) {
        stop()
        this.assetId = assetId
        waveform = null
        loadToken++
        pendingSeekTicksRemaining = 0
        playStartMillis = 0L
        seekSlider.setDisplayedFraction(0.0)
        seekSlider.active = false
        val token = loadToken
        SoundWaveform
            .decode(minecraft, assetId)
            .whenComplete { data, error -> minecraft.execute { applyWaveform(token, data, error) } }
    }

    fun reset() {
        stop()
        assetId = null
        waveform = null
        loadToken++
    }

    fun tick() {
        if (assetId == null) {
            return
        }
        onPlaybackChanged()
        val sound = currentSound
        if (pendingSeekTicksRemaining > 0 && sound != null) {
            SoundSeekSupport.seek(minecraft, sound, pendingSeekSeconds)
            pendingSeekTicksRemaining--
        }
    }

    fun togglePlayback() {
        val id = assetId ?: return
        if (activeSound() != null) {
            stop()
        } else {
            startPlayback(id)
            playStartMillis = System.currentTimeMillis()
        }
        onPlaybackChanged()
    }

    private fun startPlayback(id: Identifier) {
        stop()
        val sound = AssetSoundInstance(id)
        currentSound = sound
        minecraft.soundManager.play(sound)
    }

    private fun activeSound(): AssetSoundInstance? = currentSound?.takeIf { minecraft.soundManager.isActive(it) }

    private fun stop() {
        val sound = currentSound
        if (sound != null) {
            minecraft.soundManager.stop(sound)
            currentSound = null
        }
        pendingSeekTicksRemaining = 0
    }

    fun isPlaying(): Boolean {
        val playing = activeSound() != null
        if (!playing) {
            currentSound = null
        }
        return playing
    }

    fun onSeekCommitted(fraction: Double) {
        val id = assetId
        val wave = waveform
        if (id == null || wave == null) {
            return
        }
        val seconds = (fraction * wave.durationSeconds).toFloat()
        playStartMillis = System.currentTimeMillis() - (seconds * 1000L).toLong()
        val sound = activeSound()
        if (sound != null) {
            SoundSeekSupport.seek(minecraft, sound, seconds)
            return
        }
        startPlayback(id)
        onPlaybackChanged()
        pendingSeekSeconds = seconds
        pendingSeekTicksRemaining = FRESH_START_SEEK_RETRY_TICKS
    }

    fun currentPlaybackFraction(): Double {
        val wave = waveform
        if (wave == null || wave.durationSeconds <= 0F || activeSound() == null) {
            return 0.0
        }
        val elapsed = (System.currentTimeMillis() - playStartMillis) / 1000.0
        return (elapsed / wave.durationSeconds).coerceIn(0.0, 1.0)
    }

    private fun applyWaveform(
        token: Int,
        data: SoundWaveform?,
        error: Throwable?,
    ) {
        if (token != loadToken) {
            return
        }
        if (error != null || data == null) {
            onInfoLine("Duration: unknown (could not decode)")
            return
        }
        waveform = data

        seekSlider.active = true
        onInfoLine("Duration: " + formatDuration(data.durationSeconds))
    }

    companion object {
        private const val FRESH_START_SEEK_RETRY_TICKS = 3

        private fun formatDuration(seconds: Float): String {
            val total = seconds.roundToInt()
            return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
        }
    }
}
