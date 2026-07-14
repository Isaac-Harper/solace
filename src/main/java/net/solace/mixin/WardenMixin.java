package net.solace.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.solace.SolaceState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M3: the warden's anger/roar pipeline gates on its own canTargetEntity and never
 * consults {@code LivingEntity#canAttack}, so without this veto it would stalk and
 * roar at Solace players in a loop (vanilla invalidation erases the target a tick
 * later, then anger re-derives it).
 */
@Mixin(Warden.class)
public abstract class WardenMixin {

    @Inject(method = "canTargetEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void solace$wardenIgnoresSolace(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (SolaceState.isSolacePlayer(target)) {
            cir.setReturnValue(false);
        }
    }
}
