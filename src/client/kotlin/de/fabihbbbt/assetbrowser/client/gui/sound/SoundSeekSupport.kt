package de.fabihbbbt.assetbrowser.client.gui.sound

import de.fabihbbbt.assetbrowser.mixin.client.ChannelAccessor
import de.fabihbbbt.assetbrowser.mixin.client.SoundEngineAccessor
import de.fabihbbbt.assetbrowser.mixin.client.SoundManagerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import org.lwjgl.openal.AL11

object SoundSeekSupport {
    fun seek(
        minecraft: Minecraft,
        instance: SoundInstance,
        seconds: Float,
    ) {
        val engine = (minecraft.soundManager as SoundManagerAccessor).`assetBrowser$getSoundEngine`()
        val handle = (engine as SoundEngineAccessor).`assetBrowser$getInstanceToChannel`()[instance]
        handle?.execute { channel ->
            AL11.alSourcef((channel as ChannelAccessor).`assetBrowser$getSource`(), AL11.AL_SEC_OFFSET, seconds)
        }
    }
}
