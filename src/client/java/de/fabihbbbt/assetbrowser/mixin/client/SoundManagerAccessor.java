package de.fabihbbbt.assetbrowser.mixin.client;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@link SoundEngine} behind a {@link SoundManager} so preview playback
 * (see {@code SoundSeekSupport} in the {@code client.gui} package) can reach the channel for a
 * specific sound instance and seek within it.
 */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {

    @Accessor("soundEngine")
    SoundEngine assetBrowser$getSoundEngine();
}
