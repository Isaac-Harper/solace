package net.solace.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.solace.Feature;
import net.solace.Features;
import net.solace.FlightLogic;
import net.solace.SlotLogic;
import net.solace.Solace;
import net.solace.SolaceData;
import net.solace.SolaceState;
import net.solace.config.SolaceConfig;

import java.util.UUID;

/**
 * M6/M7: per-tick perks (once per second) for Solace players.
 *
 * <p>Comfort: no phantoms (baseline), no hunger, night vision.
 * Creative-lite: flight, faster mining, reach, keep-repaired gear.
 * Flight and reach are reconciled for <em>all</em> players so they are revoked
 * cleanly when Solace (or the feature) is turned off; commands call
 * {@link #reconcileNow} after state writes so changes apply instantly.
 */
public final class ComfortTicker {

    private ComfortTicker() {
    }

    private static final int INTERVAL_TICKS = 20; // once per second
    private static final Identifier REACH_MODIFIER = Identifier.fromNamespaceAndPath(Solace.MOD_ID, "reach");
    private static final double REACH_BONUS = 3.0;
    private static final Stat<?> TIME_SINCE_REST = Stats.CUSTOM.get(Stats.TIME_SINCE_REST);

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % INTERVAL_TICKS != 0) {
                return;
            }
            SolaceConfig config = SolaceConfig.get();
            if (config.allowMultiplePlayers) {
                // The slot only exists while the limit is on; drop stale claims otherwise
                // so an ancient holder cannot deadlock a future flip back to limited.
                if (SolaceState.slotHolder(server) != null) {
                    SolaceState.setSlotHolder(server, null);
                }
            } else {
                // Resolve a stale holder (online but disabled) BEFORE the per-player pass,
                // or the pass could force-disable a legitimate player first and only then
                // release the slot, leaving nobody with Solace.
                UUID holder = SolaceState.slotHolder(server);
                ServerPlayer holderPlayer = (holder == null) ? null : server.getPlayerList().getPlayer(holder);
                if (holderPlayer != null && !SolaceState.isEnabled(holderPlayer)) {
                    SolaceState.setSlotHolder(server, null);
                }
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                perPlayerTick(server, player, config);
            }
        });
    }

    private static void perPlayerTick(MinecraftServer server, ServerPlayer player, SolaceConfig config) {
        SolaceData data = SolaceState.get(player);

        if (!config.allowMultiplePlayers) {
            data = enforceSlot(server, player, data);
        }

        reconcileNow(player, data, config);

        if (!data.enabled()) {
            return;
        }

        // Baseline: void rescue. Immunity cancels void damage, so warp anyone who
        // fell out of the world back to their respawn point instead. (Descending
        // disabled players are covered by the flight machine's DESCENT_SAFETY.)
        rescueFromVoid(player);

        // Baseline: no phantoms, keep "time since rest" pinned below the spawn threshold.
        if (player.getStats().getValue(TIME_SINCE_REST) > 0) {
            player.getStats().setValue(player, TIME_SINCE_REST, 0);
        }

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
            topUpEffect(player, MobEffects.NIGHT_VISION, 0, false);
        }

        // Creative-lite: faster mining (Haste III).
        if (Features.effective(data, Feature.MINING_BOOST, config)) {
            topUpEffect(player, MobEffects.HASTE, 2, false);
        }

        // Creative-lite: keep equipped gear repaired. The durability mixin stops
        // new damage; this heals gear damaged before enabling or equipped damaged.
        if (Features.effective(data, Feature.NO_DURABILITY, config)) {
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.isDamaged()) {
                    stack.setDamageValue(0);
                }
            }
        }
    }

    /** Apply {@link SlotLogic}'s decision for this player. Only called while the limit is on. */
    private static SolaceData enforceSlot(MinecraftServer server, ServerPlayer player, SolaceData data) {
        switch (SlotLogic.decide(SolaceState.slotHolder(server), player.getUUID(), data.enabled())) {
            case CLAIM -> SolaceState.setSlotHolder(server, player.getUUID());
            case RELEASE -> SolaceState.setSlotHolder(server, null);
            case FORCE_DISABLE -> {
                data = data.withEnabled(false);
                SolaceState.set(player, data);
                player.sendSystemMessage(Component.literal(
                        "Solace was disabled: only one Solace player is allowed on this server."));
                Solace.LOGGER.info("Disabled Solace for {} (the slot is held by another player).",
                        player.getName().getString());
            }
            case NONE -> {
            }
        }
        return data;
    }

    /** Recompute flight and reach from current state. Called by the ticker and after every command state write. */
    public static void reconcileNow(ServerPlayer player) {
        reconcileNow(player, SolaceState.get(player), SolaceConfig.get());
    }

    private static void reconcileNow(ServerPlayer player, SolaceData data, SolaceConfig config) {
        boolean enabled = data.enabled();
        applyFlight(player, enabled && Features.effective(data, Feature.FLIGHT, config));
        reconcileReach(player, enabled && Features.effective(data, Feature.REACH_BOOST, config));
    }

    /** Apply {@link FlightLogic}'s decision for this player. */
    private static void applyFlight(ServerPlayer player, boolean desired) {
        if (player.isCreative() || player.isSpectator()) {
            return; // creative/spectator manage their own flight; markers resolve on return to survival
        }
        Abilities abilities = player.getAbilities();
        boolean landed = player.onGround() || player.isInWater();
        boolean suspended = player.isPassenger() || player.isFallFlying();
        FlightLogic.Action action = FlightLogic.decide(
                SolaceState.flightMarker(player), desired, abilities.mayfly, landed, suspended);
        switch (action) {
            case GRANT -> {
                abilities.mayfly = true;
                player.onUpdateAbilities();
                SolaceState.setFlightMarker(player, FlightLogic.Marker.GRANTED);
            }
            case ADOPT -> SolaceState.setFlightMarker(player, FlightLogic.Marker.GRANTED);
            case REVOKE_TO_DESCENT -> {
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
                SolaceState.setFlightMarker(player, FlightLogic.Marker.DESCENDING);
                if (!suspended) {
                    applyDescentSafety(player);
                }
            }
            case REVOKE_AND_CLEAR -> {
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
                SolaceState.setFlightMarker(player, null);
                clearDescentSafety(player);
            }
            case LOST_FLIGHT_TO_DESCENT -> {
                SolaceState.setFlightMarker(player, FlightLogic.Marker.DESCENDING);
                if (!suspended) {
                    applyDescentSafety(player);
                }
            }
            case DESCENT_SAFETY -> applyDescentSafety(player);
            case FOREIGN_GRANT_CLEAR, LAND_CLEAR -> {
                SolaceState.setFlightMarker(player, null);
                clearDescentSafety(player);
            }
            case HOLD, NONE -> {
            }
        }
    }

    /** Fall-safety for a revoked flyer: immunity may already be off, so land them gently. */
    private static void applyDescentSafety(ServerPlayer player) {
        topUpEffect(player, MobEffects.SLOW_FALLING, 0, true);
        player.fallDistance = 0;
        rescueFromVoid(player);
    }

    /** The descent is over: remove our slow falling (ambient marks it as ours, potions are not). */
    private static void clearDescentSafety(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.SLOW_FALLING);
        if (current != null && current.isAmbient()) {
            player.removeEffect(MobEffects.SLOW_FALLING);
        }
    }

    private static void topUpEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, boolean showIcon) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 300) {
            player.addEffect(new MobEffectInstance(effect, 600, amplifier, true, false, showIcon));
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

    /**
     * One-shot on the enable edge: drop existing aggro aimed at this player, including
     * the anger memories brain mobs re-derive targets from. New aggro is blocked at
     * the source (Mob#setTarget, LivingEntity#canAttack, Warden#canTargetEntity), and
     * vanilla's own target invalidation runs against canAttack every brain tick, so
     * no periodic sweep is needed.
     */
    public static void clearAggro(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(48.0);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area)) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            Brain<?> brain = mob.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)
                    && player.getUUID().equals(brain.getMemory(MemoryModuleType.ANGRY_AT).orElse(null))) {
                brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            }
        }
    }

    /**
     * If the player has fallen out of the world, warp them to their respawn point
     * (bed/anchor, or world spawn). Immunity already cancels void damage, so without
     * this a Solace player in the void would fall forever.
     */
    private static void rescueFromVoid(ServerPlayer player) {
        if (player.getY() >= ((ServerLevel) player.level()).getMinY() - 12) {
            return;
        }
        TeleportTransition respawn = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
        player.teleport(respawn);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
    }
}
