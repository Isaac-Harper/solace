package net.solace.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.solace.Feature;
import net.solace.Features;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M7: no durability. Zeroes the durability change at its single chokepoint,
 * {@code processDurabilityChange}, which every {@code hurtAndBreak} overload and
 * {@code hurtWithoutBreaking} funnel through, so a tool can never break mid-swing.
 * The player is null when a mob's gear takes durability damage (vanilla passes null
 * for non-player holders), hence the guard, mirroring vanilla's own creative check.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(
            method = "processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)I",
            at = @At("HEAD"),
            cancellable = true)
    private void solace$noDurability(int amount, ServerLevel level, ServerPlayer player,
                                     CallbackInfoReturnable<Integer> cir) {
        if (player != null && Features.active(player, Feature.NO_DURABILITY)) {
            cir.setReturnValue(0);
        }
    }
}
