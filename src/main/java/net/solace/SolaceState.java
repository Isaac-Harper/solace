package net.solace;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Registers and exposes the per-player Solace attachment.
 */
public final class SolaceState {

    public static final AttachmentType<SolaceData> DATA = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(Solace.MOD_ID, "data"),
            SolaceData.CODEC
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

    public static void set(Entity entity, SolaceData data) {
        entity.setAttached(DATA, data);
    }
}
