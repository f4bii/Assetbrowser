package de.fabihbbbt.assetbrowser.client.gui.sound

import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

class AssetSoundInstance(
    assetId: Identifier,
) : AbstractSoundInstance(assetId, SoundSource.MASTER, RandomSource.create()) {
    init {
        attenuation = SoundInstance.Attenuation.NONE
        relative = true
        volume = 1.0F
        pitch = 1.0F
    }

    override fun resolve(soundManager: SoundManager): WeighedSoundEvents {
        val fileLocation = Sound.SOUND_LISTER.fileToId(identifier)
        sound = Sound(fileLocation, { 1.0F }, { 1.0F }, 1, Sound.Type.FILE, false, false, 16)
        return WeighedSoundEvents(identifier, null)
    }
}
