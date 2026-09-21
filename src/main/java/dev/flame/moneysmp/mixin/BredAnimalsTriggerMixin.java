package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.advancements.criterion.BredAnimalsTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BredAnimalsTrigger.class)
public abstract class BredAnimalsTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void moneysmp$unlockout(ServerPlayer player, Animal parent, Animal partner, AgeableMob child, CallbackInfo ci) {
        Unlockout.bred(player, parent);
    }
}
