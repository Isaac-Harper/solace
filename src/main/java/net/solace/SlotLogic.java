package net.solace;

import java.util.UUID;

/**
 * Pure decision table for the single-Solace-player slot (allowMultiplePlayers=false).
 * The slot only exists while the limit is on; when the limit is off the ticker clears
 * any stale holder, and flipping the limit back on re-derives the holder from the
 * first online enabled player.
 */
public final class SlotLogic {

    public enum Action {
        /** The slot is free and this enabled player takes it. */
        CLAIM,
        /** Someone else holds the slot: disable this player's Solace. */
        FORCE_DISABLE,
        /** The holder no longer has Solace enabled: free the slot. */
        RELEASE,
        NONE
    }

    private SlotLogic() {
    }

    /** Per-player decision while the limit is on. */
    public static Action decide(UUID holder, UUID player, boolean enabled) {
        if (enabled) {
            if (holder == null) {
                return Action.CLAIM;
            }
            return holder.equals(player) ? Action.NONE : Action.FORCE_DISABLE;
        }
        return player.equals(holder) ? Action.RELEASE : Action.NONE;
    }
}
