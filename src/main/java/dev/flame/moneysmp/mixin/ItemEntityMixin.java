package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Altar;
import dev.flame.moneysmp.Legends;
import dev.flame.moneysmp.Weapons;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// covers every way a fragment ends up on the ground: the constructor, death drops and
// /drop all go through setItem
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "setItem", at = @At("TAIL"))
    private void moneysmp$altar(ItemStack stack, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (Altar.carries(stack)) Altar.protect(self);
        // a Crazy Slots transformation can't leave its holder; dropped, it is the slots again
        if (Legends.slotsUntil(stack) > 0) self.setItem(Weapons.revert(stack));
    }
}
