package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Altar;
import dev.flame.moneysmp.Weapons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    // ItemEntity doesn't override remove, so this is the one place every item death passes
    @Inject(method = "remove", at = @At("HEAD"))
    private void moneysmp$altar(Entity.RemovalReason reason, CallbackInfo ci) {
        if (reason.shouldDestroy() && (Object) this instanceof ItemEntity it) Altar.destroyed(it);
    }

    // players hidden by a legendary, and a disguise's shell kept out of its wearer's view.
    // ServerPlayer's override calls this through super
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void moneysmp$hidden(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        if (!Weapons.visible((Entity) (Object) this, viewer)) cir.setReturnValue(false);
    }
}
