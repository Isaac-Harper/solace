package net.solace.mixin;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.solace.SolaceState;
import net.solace.config.SolaceConfig;
import net.solace.event.SafetyEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M3/M8: two vetoes on LivingEntity.
 *
 * <p>canAttack is the PRIMARY mobs-ignore veto: goal AI (TargetGoal via
 * TargetingConditions), brain AI acquisition (StartAttacking, sensors via
 * TargetingConditions), and brain target invalidation (StopAttackingIfTargetInvalid)
 * all funnel through it. MobMixin covers direct setTarget calls and WardenMixin the
 * warden's private gate; do not remove this veto in favor of either.
 *
 * <p>addEffect: pacifist Solace players cannot apply harmful effects (splash/lingering
 * potion ticks carry no attacker on their damage source, so the ALLOW_DAMAGE veto in
 * SafetyEvents cannot catch them; the effect application does carry the source).
 * Non-hostile uses of nominally harmful effects stay allowed: Weakness deals no
 * damage and starts the zombie-villager cure, and Instant Damage heals the undead.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void solace$neverTargetSolace(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (SolaceState.isSolacePlayer(target)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void solace$pacifistNoHarmfulEffects(MobEffectInstance instance, Entity source,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (source == null || instance.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        if (instance.getEffect().value() == MobEffects.WEAKNESS.value()) {
            return; // no damage; the zombie-villager cure mechanic
        }
        LivingEntity target = (LivingEntity) (Object) this;
        if (instance.getEffect().value() == MobEffects.INSTANT_DAMAGE.value()
                && target.isInvertedHealAndHarm()) {
            return; // heals the undead, another curing/utility use
        }
        if (SolaceConfig.get().pacifist) {
            Player attacker = SafetyEvents.attackingPlayer(source);
            if (attacker != null && SolaceState.isEnabled(attacker) && target != attacker) {
                cir.setReturnValue(false);
            }
        }
    }
}
