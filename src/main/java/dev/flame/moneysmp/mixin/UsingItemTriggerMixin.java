package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.advancements.criterion.UsingItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UsingItemTrigger.class)
public abstract class UsingItemTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void moneysmp$unlockout(ServerPlayer player, ItemStack stack, CallbackInfo ci) {
        Unlockout.using(player, stack);
    }
}
