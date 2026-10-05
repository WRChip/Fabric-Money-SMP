package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Altar;
import dev.flame.moneysmp.Legends;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
    // every way of putting something in a slot (click, shift-click, number key, drag) asks
    // mayPlace first, so this alone keeps fragments and maces out of ender chests
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void moneysmp$altar(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (((Slot) (Object) this).container instanceof PlayerEnderChestContainer && Altar.stash(stack)) cir.setReturnValue(false);
    }

    // a Crazy Slots transformation stays in the slot it was rolled in
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void moneysmp$slots(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (Legends.slotsUntil(((Slot) (Object) this).getItem()) > 0) cir.setReturnValue(false);
    }
}
