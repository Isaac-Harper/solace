package net.solace.mixin;

import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.solace.config.SolaceConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M8: base protection. Decides whether an explosion may destroy blocks, mirroring how the
 * vanilla mobGriefing rule disables creeper block damage. Entity damage is untouched, so mobs
 * still threaten non-Solace players; only block destruction is suppressed.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @Inject(method = "canTriggerBlocks", at = @At("HEAD"), cancellable = true)
    private void solace$baseProtection(CallbackInfoReturnable<Boolean> cir) {
        SolaceConfig config = SolaceConfig.get();
        String mode = config.baseProtection.resolvedMode();
        if (SolaceConfig.BaseProtection.OFF.equals(mode)) {
            return;
        }
        Explosion self = (Explosion) (Object) this;

        if (SolaceConfig.BaseProtection.ALL_EXPLOSIONS.equals(mode)) {
            cir.setReturnValue(false);
            return;
        }

        if (SolaceConfig.BaseProtection.HOME_REGION.equals(mode)) {
            SolaceConfig.Home home = config.home;
            if (home != null && home.matches(self.level())) {
                double radius = config.baseProtection.homeRadius;
                if (self.center().distanceToSqr(home.x, home.y, home.z) <= radius * radius) {
                    cir.setReturnValue(false);
                }
            }
            return;
        }

        // "hostile_explosions" (default): cancel block damage from hostile-mob explosions
        // (creeper, ghast, wither, …), leaving TNT and bed/anchor explosions working.
        if (self.getDirectSourceEntity() instanceof Enemy || self.getIndirectSourceEntity() instanceof Enemy) {
            cir.setReturnValue(false);
        }
    }
}
