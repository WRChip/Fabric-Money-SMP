package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// RAID_WIN only says who won, not which raid. remember the one being ticked
@Mixin(Raid.class)
public abstract class RaidMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void moneysmp$unlockout(ServerLevel level, CallbackInfo ci) {
        Unlockout.tickingRaid = (Raid) (Object) this;
    }
}
