package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import net.minecraft.world.level.block.entity.vault.VaultSharedData;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// only the branch that actually ejects loot reaches addToRewardedPlayers
@Mixin(VaultBlockEntity.Server.class)
public abstract class VaultBlockEntityServerMixin {
    @Inject(method = "tryInsertKey", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/vault/VaultServerData;addToRewardedPlayers(Lnet/minecraft/world/entity/player/Player;)V"))
    private static void moneysmp$unlockout(ServerLevel level, BlockPos pos, BlockState state, VaultConfig config, VaultServerData data,
                                           VaultSharedData shared, Player player, ItemStack key, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp) Unlockout.vaultOpened(sp, state.getValue(VaultBlock.OMINOUS));
    }
}
