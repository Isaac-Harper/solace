package net.solace.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.solace.Feature;
import net.solace.Features;
import net.solace.Solace;
import net.solace.SolaceData;
import net.solace.SolaceState;
import net.solace.config.SolaceConfig;

/**
 * M6/M7 — per-tick perks (once per second) for Solace players.
 *
 * <p>Comfort: no phantoms (baseline), no hunger, night vision.
 * Creative-lite: flight, faster mining, reach, unbreakable gear.
 * Flight and reach are reconciled for <em>all</em> players so they are revoked
 * cleanly when Solace (or the feature) is turned off.
 */
public final class ComfortTicker {

    private ComfortTicker() {
    }

    private static final int INTERVAL_TICKS = 20; // once per second
    private static final Identifier REACH_MODIFIER = Identifier.fromNamespaceAndPath(Solace.MOD_ID, "reach");
    private static final double REACH_BONUS = 3.0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % INTERVAL_TICKS != 0) {
                return;
            }
            SolaceConfig config = SolaceConfig.get();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                SolaceData data = SolaceState.get(player);
                boolean enabled = data.enabled();

                // Reconciled for everyone so they revoke when Solace/feature turns off.
                reconcileFlight(player, enabled && Features.effective(data, Feature.FLIGHT, config));
                reconcileReach(player, enabled && Features.effective(data, Feature.REACH_BOOST, config));

                if (!enabled) {
                    continue;
                }

                // Baseline: no phantoms — keep "time since rest" pinned below the spawn threshold.
                player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 0);

                // Baseline: shoo off any mob already targeting them (the mixin only blocks NEW aggro).
                clearAggro(player);

                // Comfort: no hunger.
                if (Features.effective(data, Feature.NO_HUNGER, config)) {
                    FoodData food = player.getFoodData();
                    if (food.getFoodLevel() < 20) {
                        food.setFoodLevel(20);
                    }
                    if (food.getSaturationLevel() < 5.0f) {
                        food.setSaturation(5.0f);
                    }
                }

                // Comfort: night vision.
                if (Features.effective(data, Feature.NIGHT_VISION, config)) {
                    refreshHidden(player, MobEffects.NIGHT_VISION, 0);
                }

                // Creative-lite: faster mining (Haste III).
                if (Features.effective(data, Feature.MINING_BOOST, config)) {
                    refreshHidden(player, MobEffects.HASTE, 2);
                }

                // Creative-lite: unbreakable gear — keep equipped items repaired.
                if (Features.effective(data, Feature.NO_DURABILITY, config)) {
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        ItemStack stack = player.getItemBySlot(slot);
                        if (!stack.isEmpty() && stack.isDamaged()) {
                            stack.setDamageValue(0);
                        }
                    }
                }
            }
        });
    }

    private static void refreshHidden(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 300) {
            player.addEffect(new MobEffectInstance(effect, 600, amplifier, true, false, false));
        }
    }

    private static void reconcileFlight(ServerPlayer player, boolean desired) {
        if (player.isCreative() || player.isSpectator()) {
            return; // creative/spectator manage their own flight
        }
        Abilities abilities = player.getAbilities();
        if (desired && !abilities.mayfly) {
            abilities.mayfly = true;
            player.onUpdateAbilities();
        } else if (!desired && abilities.mayfly) {
            abilities.mayfly = false;
            abilities.flying = false;
            player.onUpdateAbilities();
        }
    }

    private static void reconcileReach(ServerPlayer player, boolean desired) {
        applyReach(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), desired);
        applyReach(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), desired);
    }

    private static void applyReach(AttributeInstance instance, boolean desired) {
        if (instance == null) {
            return;
        }
        boolean has = instance.hasModifier(REACH_MODIFIER);
        if (desired && !has) {
            instance.addTransientModifier(
                    new AttributeModifier(REACH_MODIFIER, REACH_BONUS, AttributeModifier.Operation.ADD_VALUE));
        } else if (!desired && has) {
            instance.removeModifier(REACH_MODIFIER);
        }
    }

    /** Clear any nearby mob's aggro on this player (handles targets set before Solace was enabled). */
    public static void clearAggro(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(48.0);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area, m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
    }
}
