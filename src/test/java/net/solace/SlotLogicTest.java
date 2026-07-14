package net.solace;

import net.solace.SlotLogic.Action;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static net.solace.SlotLogic.decide;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotLogicTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void firstEnabledPlayerClaimsAFreeSlot() {
        assertEquals(Action.CLAIM, decide(null, A, true));
    }

    @Test
    void holderKeepsTheSlot() {
        assertEquals(Action.NONE, decide(A, A, true));
    }

    @Test
    void enabledNonHolderIsDisabled() {
        assertEquals(Action.FORCE_DISABLE, decide(A, B, true));
    }

    @Test
    void disabledHolderReleasesTheSlot() {
        // Self-heals persistence skew (slot claimed on disk, player data rolled back).
        assertEquals(Action.RELEASE, decide(A, A, false));
    }

    @Test
    void disabledNonHolderIsIgnored() {
        assertEquals(Action.NONE, decide(A, B, false));
        assertEquals(Action.NONE, decide(null, B, false));
    }
}
