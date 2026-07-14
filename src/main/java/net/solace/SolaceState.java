package net.solace;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Registers and exposes the per-player Solace attachments, plus the server-wide
 * Solace slot holder used by allowMultiplePlayers=false.
 */
public final class SolaceState {

    public static final AttachmentType<SolaceData> DATA = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(Solace.MOD_ID, "data"),
            builder -> builder
                    .persistent(SolaceData.CODEC)
                    .copyOnDeath()
                    // Sync each player's own state to their client only, so the Mod Menu
                    // "My Solace" panel can show accurate toggles on any server.
                    .syncWith(ByteBufCodecs.fromCodecWithRegistries(SolaceData.CODEC),
                            AttachmentSyncPredicate.targetOnly())
    );

    /**
     * Flight ownership/descent marker (see {@link FlightLogic}). Persisted because
     * vanilla persists the abilities it tracks: an in-memory marker would leak
     * permanent flight across restarts.
     */
    public static final AttachmentType<FlightLogic.Marker> FLIGHT_MARKER = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(Solace.MOD_ID, "flight_marker"),
            builder -> builder.persistent(
                    com.mojang.serialization.Codec.STRING.xmap(
                            raw -> {
                                try {
                                    return FlightLogic.Marker.valueOf(raw);
                                } catch (IllegalArgumentException e) {
                                    return FlightLogic.Marker.GRANTED; // unknown future value: safest is "we own it"
                                }
                            },
                            Enum::name)
            ).copyOnDeath()
    );

    /**
     * UUID of the player holding the single Solace slot, attached to the overworld so
     * it persists with the world. Only maintained while allowMultiplePlayers is false;
     * the ticker clears it while the limit is off and re-derives it on a flip.
     */
    public static final AttachmentType<UUID> SLOT_HOLDER = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(Solace.MOD_ID, "slot_holder"),
            builder -> builder.persistent(UUIDUtil.STRING_CODEC)
    );

    private SolaceState() {
    }

    /** Referenced from mod init to force class-load (and thus attachment registration). */
    public static void init() {
    }

    public static SolaceData get(Entity entity) {
        return entity.getAttachedOrElse(DATA, SolaceData.DEFAULT);
    }

    public static boolean isEnabled(Entity entity) {
        return get(entity).enabled();
    }

    /** The single definition of a protected Solace target, shared by every mixin veto. */
    public static boolean isSolacePlayer(Entity entity) {
        return entity instanceof Player && isEnabled(entity);
    }

    public static void set(Entity entity, SolaceData data) {
        entity.setAttached(DATA, data);
    }

    public static FlightLogic.Marker flightMarker(Entity entity) {
        return entity.getAttachedOrElse(FLIGHT_MARKER, null);
    }

    public static void setFlightMarker(Entity entity, FlightLogic.Marker marker) {
        if (marker == null) {
            entity.removeAttached(FLIGHT_MARKER);
        } else {
            entity.setAttached(FLIGHT_MARKER, marker);
        }
    }

    public static UUID slotHolder(MinecraftServer server) {
        return server.overworld().getAttachedOrElse(SLOT_HOLDER, null);
    }

    public static void setSlotHolder(MinecraftServer server, UUID holder) {
        if (holder == null) {
            server.overworld().removeAttached(SLOT_HOLDER);
        } else {
            server.overworld().setAttached(SLOT_HOLDER, holder);
        }
    }
}
