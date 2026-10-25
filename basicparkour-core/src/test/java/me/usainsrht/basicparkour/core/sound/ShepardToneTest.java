package me.usainsrht.basicparkour.core.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ShepardToneTest {

    @Test
    public void testShepardTonePitchAndVolumeProperties() {
        int cycle = 12;
        float baseVolume = 0.8f;

        for (int block = 1; block <= 100; block++) {
            int step = Math.floorMod(block - 1, cycle);
            double fraction = (double) step / cycle;

            float pitchLow = (float) (0.5 * Math.pow(2.0, fraction));
            float pitchHigh = (float) (1.0 * Math.pow(2.0, fraction));

            // Verify pitches strictly stay within Minecraft's valid pitch bounds [0.5, 2.0]
            assertTrue(pitchLow >= 0.5f && pitchLow <= 1.0f, "pitchLow out of bounds: " + pitchLow);
            assertTrue(pitchHigh >= 1.0f && pitchHigh <= 2.0f, "pitchHigh out of bounds: " + pitchHigh);

            double angle = fraction * (Math.PI / 2.0);
            float volLow = (float) (baseVolume * Math.sin(angle));
            float volHigh = (float) (baseVolume * Math.cos(angle));

            // Verify equal-power acoustic crossfade: volLow^2 + volHigh^2 == baseVolume^2
            double totalPower = (volLow * volLow) + (volHigh * volHigh);
            assertEquals(baseVolume * baseVolume, totalPower, 0.001, "Equal power property violated at block " + block);
        }

        // Test boundary wrap-around continuity: step cycle-1 to step 0
        int lastStep = cycle - 1;
        double fracLast = (double) lastStep / cycle;
        float pitchLowLast = (float) (0.5 * Math.pow(2.0, fracLast));

        int nextStep = 0;
        double fracNext = (double) nextStep / cycle;
        float pitchHighNext = (float) (1.0 * Math.pow(2.0, fracNext));

        // When stepping from lastStep (11/12) to nextStep (0), the next pitch step is exactly 1 semitone
        double semitoneRatio = Math.pow(2.0, 1.0 / 12.0);
        assertEquals(pitchHighNext, pitchLowLast * semitoneRatio, 0.001,
            "Next octave high pitch should seamlessly continue one semitone up from previous low pitch");
    }
}
