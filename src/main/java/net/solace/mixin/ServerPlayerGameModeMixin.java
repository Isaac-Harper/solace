package net.solace.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.solace.Feature;
import net.solace.Features;
import net.solace.SolaceData;
import net.solace.SolaceState;
import net.solace.config.SolaceConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M7 — infinite basic blocks / torches. Preserves the held stack's count across block placement
 * for Solace players who have the feature and are holding a tagged item — the same trick creative
 * mode uses, scoped to the {@code solace:infinite_blocks} / {@code solace:infinite_torches} tags.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Unique
    private static final TagKey<Item> SOLACE_INFINITE_BLOCKS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("solace", "infinite_blocks"));
    @Unique
    private static final TagKey<Item> SOLACE_INFINITE_TORCHES =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("solace", "infinite_torches"));

    @Unique
    private int solace$savedCount = -1;

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void solace$saveCount(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                  BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        solace$savedCount = solace$shouldPreserve(player, stack) ? stack.getCount() : -1;
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void solace$restoreCount(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                     BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (solace$savedCount >= 0 && !stack.isEmpty()) {
            stack.setCount(solace$savedCount);
        }
        solace$savedCount = -1;
    }

    @Unique
    private boolean solace$shouldPreserve(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !SolaceState.isEnabled(player)) {
            return false;
        }
        SolaceData data = SolaceState.get(player);
        SolaceConfig config = SolaceConfig.get();
        if (Features.effective(data, Feature.INFINITE_BLOCKS, config)
                && stack.is(holder -> holder.is(SOLACE_INFINITE_BLOCKS))) {
            return true;
        }
        return Features.effective(data, Feature.INFINITE_TORCHES, config)
                && stack.is(holder -> holder.is(SOLACE_INFINITE_TORCHES));
    }
}
