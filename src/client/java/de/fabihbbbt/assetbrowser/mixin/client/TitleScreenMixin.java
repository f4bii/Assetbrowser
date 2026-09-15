package de.fabihbbbt.assetbrowser.mixin.client;

import de.fabihbbbt.assetbrowser.client.gui.LoadedAssetsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a button to the top-right corner of the title screen that opens the
 * loaded-assets debug screen.
 */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin extends Screen {

    private TitleScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void assetBrowser$addLoadedAssetsButton(final CallbackInfo ci) {
        this.addRenderableWidget(LoadedAssetsScreen.openButton(this));
    }
}
