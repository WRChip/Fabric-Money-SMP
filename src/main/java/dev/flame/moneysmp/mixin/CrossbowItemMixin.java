package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Weapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vulcan's Crossbow and the Pale Cannon fire their own shots instead of what was loaded
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
    @Inject(method = "performShooting", at = @At("HEAD"), cancellable = true)
    private void moneysmp$legend(Level level, LivingEntity shooter, InteractionHand hand, ItemStack bow, float speed, float spread,
                                 LivingEntity target, CallbackInfo ci) {
        if (level instanceof ServerLevel sl && Weapons.shoot(sl, shooter, bow)) ci.cancel();
    }
}
