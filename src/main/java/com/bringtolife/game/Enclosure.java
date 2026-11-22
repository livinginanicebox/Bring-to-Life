package com.bringtolife.game;

public record Enclosure(boolean open, int volumeM3, double leakageAreaM2, int minX, int minY, int minZ) {

    public double airChangesPerHour() {
        return EnclosureScanner.airChangesPerHour(volumeM3, leakageAreaM2);
    }
}
