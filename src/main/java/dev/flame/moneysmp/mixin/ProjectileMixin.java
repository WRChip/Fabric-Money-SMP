package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Weapons;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// every projectile's hit goes through here before the subclass onHit, so a legendary shot
// (a fireball that shouldn't explode, a snowball that stuns) is handled whole and vanilla skipped
@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
    private void moneysmp$legend(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> cir) {
        if (Weapons.landed((Projectile) (Object) this, hit)) cir.setReturnValue(ProjectileDeflection.NONE);
    }
}
