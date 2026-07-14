package net.solace.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.solace.SolaceState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M3: hostile mobs ignore Solace players: refuse to ever set one as a target.
 * Covers direct setTarget calls that bypass targeting conditions; the primary
 * veto lives in LivingEntityMixin#canAttack.
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void solace$ignoreSolacePlayers(LivingEntity target, CallbackInfo ci) {
        if (SolaceState.isSolacePlayer(target)) {
            ci.cancel();
        }
    }
}
