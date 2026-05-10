package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.KeyboardCaptureManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.client.KeyboardHandler.class)
public class KeyboardHandlerMixin {

    /** Prevent pause menu from opening while keyboard capture is active. */
    @Redirect(
            method = "keyPress",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/Minecraft;pauseGame(Z)V")
    )
    private void onPauseGame(Minecraft mc, boolean flag) {
        if (!KeyboardCaptureManager.isActive()) mc.pauseGame(flag);
    }

    /** Prevent one-shot key actions (inventory, chat, hotbar, etc.) during capture. */
    @Redirect(
            method = "keyPress",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/KeyMapping;click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V")
    )
    private void onKeyMappingClick(InputConstants.Key key) {
        if (!KeyboardCaptureManager.isActive()) KeyMapping.click(key);
    }

    /**
     * Prevent keys from registering as held during capture.
     * Releases (down=false) are always forwarded so state stays consistent.
     */
    @Redirect(
            method = "keyPress",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V")
    )
    private void onKeyMappingSet(InputConstants.Key key, boolean down) {
        if (!down || !KeyboardCaptureManager.isActive()) {
            KeyMapping.set(key, down);
        }
    }
}
