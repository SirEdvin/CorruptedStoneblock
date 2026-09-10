package site.siredvin.corruptedstoneblockcore;

/** Radial widths are cumulative, upper-exclusive, and never wrap. */
public final class RingLayout {

    private RingLayout() {}

    public static int region(int x, int z, int centerX, int centerZ, int[] widths) {
        long dx = (long) x - centerX;
        long dz = (long) z - centerZ;
        long distanceSquared = dx * dx + dz * dz;
        long radius = 0;
        for (int i = 0; i < widths.length; i++) {
            radius += widths[i];
            if (distanceSquared < radius * radius) return i;
        }
        return widths.length;
    }
}
