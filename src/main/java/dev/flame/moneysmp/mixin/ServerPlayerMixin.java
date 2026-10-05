package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Legends;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// a vampire's or pale rot's mark in the tab list
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void moneysmp$species(CallbackInfoReturnable<Component> cir) {
        Component name = Legends.tabName((ServerPlayer) (Object) this);
        if (name != null) cir.setReturnValue(name);
    }
}
