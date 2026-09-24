package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// onItemPickup is only reached when the dolphin's mouth is empty and it actually takes the item
@Mixin(Dolphin.class)
public abstract class DolphinMixin {
    @Inject(method = "pickUpItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/animal/dolphin/Dolphin;onItemPickup(Lnet/minecraft/world/entity/item/ItemEntity;)V"))
    private void moneysmp$unlockout(ServerLevel level, ItemEntity item, CallbackInfo ci) {
        Unlockout.dolphinTook(item);
    }
}
