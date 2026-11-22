package com.bringtolife.game;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class EnclosureScanner {

    public static final double LEAKAGE_AREA_M2_PER_LEAKY_BLOCK = 0.02;
    public static final double DISCHARGE_COEFFICIENT = 0.6;
    public static final double STACK_EFFECT_VELOCITY_M_PER_S = 1.0;
    public static final int DEFAULT_MAX_CELLS = 4096;

    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private EnclosureScanner() {
    }

    public static Enclosure scan(EnclosureView view, int startX, int startY, int startZ) {
        return scan(view, startX, startY, startZ, DEFAULT_MAX_CELLS);
    }

    public static Enclosure scan(EnclosureView view, int startX, int startY, int startZ, int maxCells) {
        if (!view.isPassable(startX, startY, startZ)) {
            return new Enclosure(true, 0, 0.0, startX, startY, startZ);
        }
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> countedLeaks = new HashSet<>();
        frontier.add(new int[]{startX, startY, startZ});
        visited.add(pack(startX, startY, startZ));

        int volume = 0;
        double leakageArea = 0.0;
        int minX = startX;
        int minY = startY;
        int minZ = startZ;

        while (!frontier.isEmpty()) {
            int[] cell = frontier.poll();
            if (!view.isLoaded(cell[0], cell[1], cell[2])) {
                return new Enclosure(true, volume, 0.0, minX, minY, minZ);
            }
            volume++;
            if (volume > maxCells) {
                return new Enclosure(true, volume, 0.0, minX, minY, minZ);
            }
            minX = Math.min(minX, cell[0]);
            minY = Math.min(minY, cell[1]);
            minZ = Math.min(minZ, cell[2]);

            for (int[] offset : NEIGHBOURS) {
                int nx = cell[0] + offset[0];
                int ny = cell[1] + offset[1];
                int nz = cell[2] + offset[2];
                long key = pack(nx, ny, nz);
                if (visited.contains(key)) {
                    continue;
                }
                if (view.isPassable(nx, ny, nz)) {
                    visited.add(key);
                    frontier.add(new int[]{nx, ny, nz});
                } else if (view.isLeaky(nx, ny, nz) && countedLeaks.add(key)) {
                    leakageArea += LEAKAGE_AREA_M2_PER_LEAKY_BLOCK;
                }
            }
        }
        return new Enclosure(false, volume, leakageArea, minX, minY, minZ);
    }

    public static double airChangesPerHour(double volumeM3, double leakageAreaM2) {
        if (!(volumeM3 > 0.0) || !(leakageAreaM2 > 0.0)) {
            return 0.0;
        }
        double flowM3PerS = DISCHARGE_COEFFICIENT * leakageAreaM2 * STACK_EFFECT_VELOCITY_M_PER_S;
        return flowM3PerS * 3600.0 / volumeM3;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }
}
