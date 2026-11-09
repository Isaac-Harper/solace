package net.solace.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.solace.config.SolaceConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M8: base protection. Decides whether an explosion may destroy blocks or start fires,
 * mirroring how the vanilla mobGriefing rule disables creeper block damage and ghast fire.
 * Both gates sit in {@link ServerExplosion#explode()}, after entities are hurt, so entity
 * damage, knockback, particles and sound are untouched: mobs still threaten non-Solace
 * players. Wind charges only trigger blocks (buttons, doors, ...) and never destroy them,
 * so they always behave as vanilla.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @ModifyExpressionValue(method = "explode",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;interactsWithBlocks()Z"))
    private boolean solace$protectBlocks(boolean interactsWithBlocks) {
        return interactsWithBlocks && !solace$isProtected();
    }

    @ModifyExpressionValue(method = "explode",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/ServerExplosion;fire:Z"))
    private boolean solace$protectFromFire(boolean fire) {
        return fire && !solace$isProtected();
    }

    @Unique
    private boolean solace$isProtected() {
        Explosion self = (Explosion) (Object) this;
        if (self.getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK) {
            return false;
        }
        SolaceConfig config = SolaceConfig.get();
        String mode = config.baseProtection.resolvedMode();
        if (SolaceConfig.BaseProtection.OFF.equals(mode)) {
            return false;
        }

        if (SolaceConfig.BaseProtection.ALL_EXPLOSIONS.equals(mode)) {
            return true;
        }

        if (SolaceConfig.BaseProtection.HOME_REGION.equals(mode)) {
            SolaceConfig.Home home = config.home;
            if (home == null || !home.matches(self.level())) {
                return false;
            }
            double radius = config.baseProtection.homeRadius;
            return self.center().distanceToSqr(home.x, home.y, home.z) <= radius * radius;
        }

        // "hostile_explosions" (default): protect against hostile-mob explosions
        // (creeper, ghast fireball, wither, …), leaving TNT and bed/anchor explosions working.
        return self.getDirectSourceEntity() instanceof Enemy || self.getIndirectSourceEntity() instanceof Enemy;
    }
}
