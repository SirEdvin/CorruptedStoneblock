package site.siredvin.corruptedstoneblockcore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingLayoutTest {

    private static final int[] WIDTHS = { 512, 512, 1024, 1024 };

    private int region(int x, int z) {
        return RingLayout.region(x, z, 0, 0, WIDTHS);
    }

    @Test
    void boundariesAndSymmetry() {
        int[] boundaries = { 512, 1024, 2048, 3072 };
        for (int i = 0; i < boundaries.length; i++) {
            int r = boundaries[i];
            for (int s : new int[] { -1, 1 }) {
                assertEquals(i, region(s * (r - 1), 0));
                assertEquals(i + 1, region(s * r, 0));
                assertEquals(i, region(0, s * (r - 1)));
                assertEquals(i + 1, region(0, s * r));
            }
        }
        assertEquals(0, region(0, 0));
    }

    @Test
    void circlesNotSquares() {
        assertEquals(0, region(300, 400));
        assertEquals(1, region(400, 400));
        assertEquals(4, region(2500, 2500));
    }

    @Test
    void noRepeatOrIntegerOverflow() {
        for (int d : new int[] { 3072, 6144, 65536, 1000000, 29999984 }) {
            assertEquals(4, region(d, d));
            assertEquals(4, region(-d, -d));
        }
    }

    @Test
    void translatedCenter() {
        assertEquals(0, RingLayout.region(255, 255, 255, 255, WIDTHS));
        assertEquals(1, RingLayout.region(767, 255, 255, 255, WIDTHS));
        assertEquals(4, RingLayout.region(-29999984, -29999984, 255, 255, WIDTHS));
    }
}
