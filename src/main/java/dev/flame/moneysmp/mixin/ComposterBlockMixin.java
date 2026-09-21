package dev.flame.moneysmp.mixin;

import dev.flame.moneysmp.Unlockout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// addItem is only reached when the item is compostable and the composter has room
@Mixin(ComposterBlock.class)
public abstract class ComposterBlockMixin {
    @Inject(method = "useItemOn", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/ComposterBlock;addItem(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private void moneysmp$unlockout(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                    BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer sp) Unlockout.composted(sp, stack);
    }
}
