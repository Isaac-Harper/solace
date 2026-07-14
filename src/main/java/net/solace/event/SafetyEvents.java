package net.solace.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.solace.SolaceState;
import net.solace.config.SolaceConfig;

/**
 * M2: core safety. All events are server-side authoritative.
 */
public final class SafetyEvents {

    private SafetyEvents() {
    }

    public static void register() {
        // Damage immunity: cancel all incoming damage for Solace players (mobs, fall, fire,
        // drowning, suffocation, starvation, player hits, and so on).
        // Pacifist (config): also cancel damage DEALT by Solace players, whether directly,
        // via projectiles (the source entity is the shooter), or via their tamed pets.
        // Known gaps: potion effect ticks, placed lava/fire (no attacker on the source).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof Player player && SolaceState.isEnabled(player)) {
                return false;
            }
            if (SolaceConfig.get().pacifist) {
                Player attacker = attackingPlayer(source.getEntity());
                if (attacker != null && SolaceState.isEnabled(attacker)) {
                    return false;
                }
            }
            return true;
        });

        // Can't-die backstop: catches sources that bypass immunity (/kill, modded damage).
        // Cancel the death, heal to full, and extinguish; the player simply never dies.
        // Void rescue happens in ComfortTicker, not here: teleporting mid-damage-handling
        // would expose a half-processed player to the rest of the hurt pipeline.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof Player player && SolaceState.isEnabled(player)) {
                entity.setHealth(entity.getMaxHealth());
                entity.clearFire();
                return false;
            }
            return true;
        });
    }

    /**
     * The player behind an attack: the player itself, a projectile's shooter or a
     * lingering potion cloud's thrower (TraceableEntity), or a pet's owner.
     */
    public static Player attackingPlayer(Entity source) {
        if (source instanceof Player player) {
            return player;
        }
        if (source instanceof TraceableEntity traced && traced.getOwner() instanceof Player thrower) {
            return thrower;
        }
        if (source instanceof OwnableEntity pet && pet.getRootOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }
}
