package net.solace.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

/**
 * M7: infinite basic blocks / torches. Restores the held stack after block placement
 * for Solace players holding a tagged item, the same way creative mode does (a copy
 * saved before use and put back after), scoped to the {@code solace:infinite_blocks} /
 * {@code solace:infinite_torches} tags. The wrap keeps the saved copy in a local, so
 * it survives the stack emptying on its last item, re-entrant useItemOn calls from
 * modded blocks, exceptions, and other mods' cancellations.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Unique
    private static final TagKey<Item> SOLACE_INFINITE_BLOCKS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("solace", "infinite_blocks"));
    @Unique
    private static final TagKey<Item> SOLACE_INFINITE_TORCHES =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("solace", "infinite_torches"));

    @WrapMethod(method = "useItemOn")
    private InteractionResult solace$preserveInfiniteItems(ServerPlayer player, Level level, ItemStack stack,
                                                           InteractionHand hand, BlockHitResult hit,
                                                           Operation<InteractionResult> original) {
        ItemStack saved = solace$shouldPreserve(player, stack) ? stack.copy() : ItemStack.EMPTY;
        try {
            return original.call(player, level, stack, hand, hit);
        } finally {
            if (!saved.isEmpty()) {
                player.setItemInHand(hand, saved);
            }
        }
    }

    @Unique
    private boolean solace$shouldPreserve(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        SolaceData data = SolaceState.get(player);
        SolaceConfig config = SolaceConfig.get();
        if (Features.active(data, Feature.INFINITE_BLOCKS, config)
                && stack.is(holder -> holder.is(SOLACE_INFINITE_BLOCKS))) {
            return true;
        }
        return Features.active(data, Feature.INFINITE_TORCHES, config)
                && stack.is(holder -> holder.is(SOLACE_INFINITE_TORCHES));
    }
}
