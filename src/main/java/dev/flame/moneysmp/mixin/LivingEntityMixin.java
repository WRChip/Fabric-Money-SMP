package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import dev.flame.moneysmp.Weapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    // the legendaries' say on a hit: ghosts and Hyperion's fire immunity cancel it, blows on a
    // disguise go to its wearer
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void moneysmp$legendAllow(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!Weapons.allowHurt((LivingEntity) (Object) this, level, source, amount)) cir.setReturnValue(false);
    }

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float moneysmp$legendAmount(float amount, ServerLevel level, DamageSource source, float original) {
        return Weapons.modifyHurt((LivingEntity) (Object) this, source, amount);
    }

    // every hit that lands counts its raw amount, killing blows included. that is on purpose:
    // the classic tricks (a cookie-fed parrot takes Float.MAX_VALUE, a reflected ghast
    // fireball 1000) are meant to be the way to the damage goals
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void moneysmp$unlockout(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) Unlockout.damaged((LivingEntity) (Object) this, source, amount);
    }

    // a disguise's shell sits inside its wearer, so it neither pushes nor gets pushed
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void moneysmp$shellPushable(CallbackInfoReturnable<Boolean> cir) {
        if (Weapons.isShell((LivingEntity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void moneysmp$shellPush(CallbackInfo ci) {
        if (Weapons.isShell((LivingEntity) (Object) this)) ci.cancel();
    }
}
