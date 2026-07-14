package net.solace;

/**
 * Pure decision table for Solace-granted flight. The marker is persisted with the
 * player because vanilla persists the mayfly it tracks; the (marker, mayfly) pair
 * encodes: GRANTED+mayfly = Solace owns the current flight, DESCENDING+!mayfly =
 * falling under Solace's fall-safety after a revoke, no marker = Solace has no
 * stake in the player's abilities.
 */
public final class FlightLogic {

    public enum Marker {
        /** Solace granted (or adopted) the player's current mayfly. */
        GRANTED,
        /** Flight was revoked mid-air; fall-safety is owed until the player lands. */
        DESCENDING
    }

    public enum Action {
        /** Give mayfly, mark GRANTED. */
        GRANT,
        /** Mayfly already present while Solace flight is on: own it, mark GRANTED (covers upgrades from builds without the marker). */
        ADOPT,
        /** Take mayfly from an airborne player, mark DESCENDING, apply fall-safety. */
        REVOKE_TO_DESCENT,
        /** Take mayfly from a grounded player, clear the marker. */
        REVOKE_AND_CLEAR,
        /** Descending, but someone else granted mayfly: theirs now, clear the marker and leave it. */
        FOREIGN_GRANT_CLEAR,
        /** Still descending: top up fall-safety and keep the void rescue active. */
        DESCENT_SAFETY,
        /** The descent (or an externally cleared grant) ended on the ground: clear the marker. */
        LAND_CLEAR,
        /** Granted mayfly vanished externally while airborne (e.g. game mode cycle): switch to descent safety. */
        LOST_FLIGHT_TO_DESCENT,
        /** Descending but suspended (riding, gliding): keep the obligation, apply nothing this tick. */
        HOLD,
        NONE
    }

    private FlightLogic() {
    }

    /**
     * @param marker    persisted marker, null when absent
     * @param desired   whether Solace flight is effectively on for the player
     * @param mayfly    current ability
     * @param landed    terminally safe: on ground or in water. Lava is deliberately
     *                  NOT a landing: a revoked flyer sinking into lava still deserves
     *                  the descent safety. Keyed on airborne-ness, not abilities.flying:
     *                  a player falling with flight disengaged needs the same protection.
     * @param suspended temporarily carried: riding a vehicle or gliding on elytra. Not
     *                  a landing (a dismount at altitude still owes the descent safety),
     *                  but no safety is applied while it lasts (slow falling would
     *                  wreck elytra physics).
     */
    public static Action decide(Marker marker, boolean desired, boolean mayfly, boolean landed, boolean suspended) {
        if (desired) {
            if (!mayfly) {
                return Action.GRANT;
            }
            return (marker == Marker.GRANTED) ? Action.NONE : Action.ADOPT;
        }
        if (marker == Marker.GRANTED) {
            if (mayfly) {
                return (landed && !suspended) ? Action.REVOKE_AND_CLEAR : Action.REVOKE_TO_DESCENT;
            }
            return (landed && !suspended) ? Action.LAND_CLEAR : Action.LOST_FLIGHT_TO_DESCENT;
        }
        if (marker == Marker.DESCENDING) {
            if (mayfly) {
                return Action.FOREIGN_GRANT_CLEAR;
            }
            if (landed && !suspended) {
                return Action.LAND_CLEAR;
            }
            return suspended ? Action.HOLD : Action.DESCENT_SAFETY;
        }
        return Action.NONE;
    }
}
