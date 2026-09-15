package de.fabihbbbt.assetbrowser.client.gui.sound

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class SoundSeekSlider(
    private val owner: AssetSoundPreview,
) : AbstractSliderButton(0, 0, 0, 0, Component.empty(), 0.0) {
    private var dragging = false

    fun setDisplayedFraction(fraction: Double) {
        this.value = fraction.coerceIn(0.0, 1.0)
    }

    fun isDragging(): Boolean = dragging

    override fun onClick(
        event: MouseButtonEvent,
        doubleClick: Boolean,
    ) {
        super.onClick(event, doubleClick)
        dragging = true
    }

    override fun onRelease(event: MouseButtonEvent) {
        super.onRelease(event)
        dragging = false
        owner.onSeekCommitted(this.value)
    }

    override fun updateMessage() {
        setMessage(Component.literal("Seek"))
    }

    override fun applyValue() {
    }

    override fun extractWidgetRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        a: Float,
    ) {
        val waveform = owner.waveform
        if (waveform != null) {
            waveform.render(graphics, x, y, width, height, this.value)
        } else {
            SoundWaveform.renderPlaceholder(graphics, x, y, width, height, this.value)
        }
    }
}
