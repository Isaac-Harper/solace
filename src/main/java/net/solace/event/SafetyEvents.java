package net.solace.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.player.Player;
import net.solace.SolaceState;

/**
 * M2 — core safety. Both events are server-side authoritative.
 */
public final class SafetyEvents {

    private SafetyEvents() {
    }

    public static void register() {
        // Damage immunity: cancel all incoming damage for Solace players (mobs, fall, fire,
        // drowning, suffocation, starvation, player hits, …).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof Player player && SolaceState.isEnabled(player)) {
                return false;
            }
            return true;
        });

        // Can't-die backstop: catches sources that bypass immunity (void, /kill). Cancel the
        // death, heal to full, and extinguish — the player simply never dies.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof Player player && SolaceState.isEnabled(player)) {
                entity.setHealth(entity.getMaxHealth());
                entity.clearFire();
                return false;
            }
            return true;
        });
    }
}
