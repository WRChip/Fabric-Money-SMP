package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Legends;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// legendary parts are clay balls and amethyst shards underneath; only the altars take them,
// so a Warden's Heart can't be fired into a brick or a Hyperion Shard built into a spyglass
@Mixin(Ingredient.class)
public abstract class IngredientMixin {
    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void moneysmp$legend(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Legends.id(stack) != null) cir.setReturnValue(false);
    }
}
