package net.solace.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.solace.config.SolaceConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M8: base protection. Decides whether an explosion may destroy blocks, start fires or
 * affect block-like entities, mirroring how the vanilla mobGriefing rule disables creeper
 * block damage and ghast fire. Blocks and fire are gated in {@link ServerExplosion#explode()};
 * item frames, paintings, leash knots, armor stands and dropped items ignore the explosion
 * through {@link ServerExplosion#shouldAffectBlocklikeEntities()}. Mobs and players are
 * still damaged and knocked back, and particles and sound are untouched, so mobs still
 * threaten non-Solace players. Wind charges only trigger blocks (buttons, doors, ...) and
 * never destroy them, so they always behave as vanilla.
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

    @ModifyReturnValue(method = "shouldAffectBlocklikeEntities", at = @At("RETURN"))
    private boolean solace$protectBlocklikeEntities(boolean affects) {
        return affects && !solace$isProtected();
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
        Entity direct = self.getDirectSourceEntity();
        return direct instanceof Enemy
                || self.getIndirectSourceEntity() instanceof Enemy
                || solace$isOwnerlessHostileProjectile(direct);
    }

    @Unique
    private static boolean solace$isOwnerlessHostileProjectile(Entity direct) {
        return (direct instanceof LargeFireball || direct instanceof WitherSkull)
                && ((Projectile) direct).getOwner() == null;
    }
}
