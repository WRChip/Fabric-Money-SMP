package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// PlayerTrigger backs a dozen criteria; only the raid win one matters here
@Mixin(PlayerTrigger.class)
public abstract class PlayerTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void moneysmp$unlockout(ServerPlayer player, CallbackInfo ci) {
        if ((Object) this == CriteriaTriggers.RAID_WIN) Unlockout.raidWon(player);
    }
}
