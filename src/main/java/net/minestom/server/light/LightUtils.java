package net.minestom.server.light;

import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;

public class LightUtils {

    public final static int DIMENSION = 16;

    public static final int ARRAY_SIZE = DIMENSION * DIMENSION * DIMENSION / (8/4); // blocks / bytes per block

    // operation type: updating
    public static byte[] set(final int x, final int y, final int z, final int value, final byte[] array) {
        return set((x & 15) | ((z & 15) << 4) | ((y & 15) << 8), value, array);
    }

    public static byte[] set(final int index, final int value, final byte[] array) {
        final int shift = (index & 1) << 2;
        final int i = index >>> 1;

        array[i] = (byte)((array[i] & (0xF0 >>> shift)) | (value << shift));
        return array;
    }

    public static void updateLightCache(Section section) {
        // Update light cache and send to players
    }

}
