package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.advancements.criterion.ConsumeItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConsumeItemTrigger.class)
public abstract class ConsumeItemTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void moneysmp$unlockout(ServerPlayer player, ItemStack stack, CallbackInfo ci) {
        Unlockout.ate(player, stack);
    }
}
