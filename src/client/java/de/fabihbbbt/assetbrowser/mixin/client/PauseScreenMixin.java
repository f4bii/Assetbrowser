package de.fabihbbbt.assetbrowser.mixin.client;

import de.fabihbbbt.assetbrowser.client.gui.LoadedAssetsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a button to the top-right corner of the in-game pause menu that opens
 * the loaded-assets debug screen. Unlike the title screen button, this one is
 * reachable while connected to a server, so it also reflects any resource
 * pack the server has pushed to the client.
 */
@Mixin(PauseScreen.class)
abstract class PauseScreenMixin extends Screen {

    private PauseScreenMixin(final Component title) {
        super(title);
    }

    @Shadow
    public abstract boolean showsPauseMenu();

    @Inject(method = "init", at = @At("TAIL"))
    private void assetBrowser$addLoadedAssetsButton(final CallbackInfo ci) {
        if (!this.showsPauseMenu()) {
            return;
        }
        this.addRenderableWidget(LoadedAssetsScreen.openButton(this));
    }
}
