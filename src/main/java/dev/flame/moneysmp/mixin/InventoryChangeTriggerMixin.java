package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryChangeTrigger.class)
public abstract class InventoryChangeTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void moneysmp$unlockout(ServerPlayer player, Inventory inventory, ItemStack stack, CallbackInfo ci) {
        Unlockout.inventoryChanged(player, stack);
    }
}
