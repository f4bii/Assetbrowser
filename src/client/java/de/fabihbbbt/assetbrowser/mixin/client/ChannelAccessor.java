package de.fabihbbbt.assetbrowser.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the raw OpenAL source handle behind a {@link Channel} so preview playback (see
 * {@code SoundSeekSupport} in the {@code client.gui} package) can issue {@code AL_SEC_OFFSET}
 * seeks that Mojang's wrapper doesn't otherwise surface.
 */
@Mixin(Channel.class)
public interface ChannelAccessor {

    @Accessor("source")
    int assetBrowser$getSource();
}
