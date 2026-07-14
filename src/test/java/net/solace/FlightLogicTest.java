package net.solace;

import net.solace.FlightLogic.Action;
import net.solace.FlightLogic.Marker;
import org.junit.jupiter.api.Test;

import static net.solace.FlightLogic.decide;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The flight state machine regressed three review rounds in a row while it lived
 * inside the ticker; every transition is pinned here.
 */
class FlightLogicTest {

    @Test
    void grantsWheneverDesiredAndAbsent() {
        for (Marker marker : new Marker[]{null, Marker.GRANTED, Marker.DESCENDING}) {
            assertEquals(Action.GRANT, decide(marker, true, false, true, false));
            assertEquals(Action.GRANT, decide(marker, true, false, false, false));
        }
    }

    @Test
    void adoptsPreexistingMayflyWhileDesired() {
        // Upgrades from builds without the marker, and re-owning after a creative detour.
        assertEquals(Action.ADOPT, decide(null, true, true, true, false));
        assertEquals(Action.ADOPT, decide(Marker.DESCENDING, true, true, false, false));
        assertEquals(Action.NONE, decide(Marker.GRANTED, true, true, false, false));
    }

    @Test
    void revokeIsGentleForAirborneAndImmediateForGrounded() {
        // Airborne includes falling with flight disengaged, not just actively gliding.
        assertEquals(Action.REVOKE_TO_DESCENT, decide(Marker.GRANTED, false, true, false, false));
        assertEquals(Action.REVOKE_AND_CLEAR, decide(Marker.GRANTED, false, true, true, false));
    }

    @Test
    void suspensionIsNotALanding() {
        // Riding a flying mount or gliding at altitude: the descent obligation survives
        // the ride so a later dismount still gets the safety.
        assertEquals(Action.REVOKE_TO_DESCENT, decide(Marker.GRANTED, false, true, true, true));
        assertEquals(Action.LOST_FLIGHT_TO_DESCENT, decide(Marker.GRANTED, false, false, true, true));
        assertEquals(Action.HOLD, decide(Marker.DESCENDING, false, false, true, true));
        assertEquals(Action.HOLD, decide(Marker.DESCENDING, false, false, false, true));
    }

    @Test
    void neverTouchesFlightItDoesNotOwn() {
        // No marker: foreign mayfly is left alone even though Solace flight is off.
        assertEquals(Action.NONE, decide(null, false, true, false, false));
        assertEquals(Action.NONE, decide(null, false, false, true, false));
    }

    @Test
    void foreignGrantDuringDescentEndsTheDescentWithoutRevoking() {
        // Another mod grants mayfly mid-descent: it is theirs, not Solace's.
        assertEquals(Action.FOREIGN_GRANT_CLEAR, decide(Marker.DESCENDING, false, true, false, false));
        assertEquals(Action.FOREIGN_GRANT_CLEAR, decide(Marker.DESCENDING, false, true, true, false));
    }

    @Test
    void descentSafetyHoldsUntilLanding() {
        assertEquals(Action.DESCENT_SAFETY, decide(Marker.DESCENDING, false, false, false, false));
        assertEquals(Action.LAND_CLEAR, decide(Marker.DESCENDING, false, false, true, false));
    }

    @Test
    void externallyClearedGrantFallsBackToDescentSafety() {
        // Game mode cycle cleared mayfly while the marker said GRANTED.
        assertEquals(Action.LOST_FLIGHT_TO_DESCENT, decide(Marker.GRANTED, false, false, false, false));
        assertEquals(Action.LAND_CLEAR, decide(Marker.GRANTED, false, false, true, false));
    }
}
