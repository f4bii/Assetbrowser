package de.fabihbbbt.assetbrowser.mixin.client;

import java.util.Map;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the sound-instance-to-channel map so preview playback (see {@code SoundSeekSupport}
 * in the {@code client.gui} package) can find the {@link ChannelAccess.ChannelHandle} for a
 * specific currently-playing sound and seek within it.
 */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {

    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> assetBrowser$getInstanceToChannel();
}
