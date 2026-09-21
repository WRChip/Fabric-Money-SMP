package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.advancements.criterion.SummonedEntityTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SummonedEntityTrigger.class)
public abstract class SummonedEntityTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void moneysmp$unlockout(ServerPlayer player, Entity entity, CallbackInfo ci) {
        Unlockout.summoned(player, entity);
    }
}
