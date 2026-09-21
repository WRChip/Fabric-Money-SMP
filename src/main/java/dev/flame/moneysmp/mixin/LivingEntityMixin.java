package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// every hit that lands counts its raw amount, killing blows included. that is on purpose:
// the classic tricks (a cookie-fed parrot takes Float.MAX_VALUE, a reflected ghast
// fireball 1000) are meant to be the way to the damage goals
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void moneysmp$unlockout(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) Unlockout.damaged((LivingEntity) (Object) this, source, amount);
    }
}
