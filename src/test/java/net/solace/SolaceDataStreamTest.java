package net.solace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client-sync payload. The attachment syncs {@link SolaceData} via a stream codec
 * wrapping {@link SolaceData#CODEC} over NBT; this round-trips through the same NBT ops
 * to lock in that a player's synced state survives serialization intact.
 */
class SolaceDataStreamTest {

    private static SolaceData roundTrip(SolaceData original) {
        DynamicOps<Tag> ops = NbtOps.INSTANCE;
        Codec<SolaceData> codec = SolaceData.CODEC;
        Tag encoded = codec.encodeStart(ops, original)
                .getOrThrow(m -> new AssertionError("encode failed: " + m));
        return codec.parse(ops, encoded)
                .getOrThrow(m -> new AssertionError("decode failed: " + m));
    }

    @Test
    void syncPayloadSurvivesRoundTrip() {
        SolaceData original = new SolaceData(true, Preset.CREATIVE_LITE,
                Map.of(Feature.FLIGHT, false, Feature.NO_DURABILITY, true), true);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void defaultAndEmptyStatesRoundTrip() {
        assertEquals(SolaceData.DEFAULT, roundTrip(SolaceData.DEFAULT));
        assertEquals(SolaceData.DEFAULT.withEnabled(true), roundTrip(SolaceData.DEFAULT.withEnabled(true)));
    }
}
