package com.bringtolife.game;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnclosureScannerTest {

    @Test
    void aSealedRoomIsMeasuredAndHasNoVentilation() {
        Enclosure enclosure = EnclosureScanner.scan(new RoomView(Set.of(), 100), 2, 2, 2);

        assertFalse(enclosure.open());
        assertEquals(27, enclosure.volumeM3());
        assertEquals(0.0, enclosure.leakageAreaM2());
        assertEquals(0.0, enclosure.airChangesPerHour());
    }

    @Test
    void airThatKeepsGoingIsTreatedAsOpen() {
        EnclosureView openSky = new EnclosureView() {
            @Override
            public boolean isPassable(int x, int y, int z) {
                return y >= 1;
            }

            @Override
            public boolean isLeaky(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isLoaded(int x, int y, int z) {
                return true;
            }
        };

        Enclosure enclosure = EnclosureScanner.scan(openSky, 0, 1, 0, 64);

        assertTrue(enclosure.open());
    }

    @Test
    void unloadedTerrainIsTreatedAsOpen() {
        Enclosure enclosure = EnclosureScanner.scan(new RoomView(Set.of(), 2), 2, 2, 2);

        assertTrue(enclosure.open());
    }

    @Test
    void aClosedDoorLeaksAirWithoutJoiningTheRoomToTheOutside() {
        Enclosure enclosure = EnclosureScanner.scan(new RoomView(Set.of(2L), 100), 2, 2, 2);

        assertFalse(enclosure.open());
        assertEquals(27, enclosure.volumeM3());
        assertEquals(0.02, enclosure.leakageAreaM2(), 1e-12);
        assertEquals(1.6, enclosure.airChangesPerHour(), 1e-9);
    }

    @Test
    void leakageScalesWithTheNumberOfOpenings() {
        assertEquals(3.2, EnclosureScanner.scan(new RoomView(Set.of(2L, 3L), 100), 2, 2, 2).airChangesPerHour(), 1e-9);
        assertEquals(0.0, EnclosureScanner.airChangesPerHour(27.0, 0.0));
    }

    private static final class RoomView implements EnclosureView {

        private final Set<Long> leakyY;
        private final int loadedBelowY;

        private RoomView(Set<Long> leakyY, int loadedBelowY) {
            this.leakyY = leakyY;
            this.loadedBelowY = loadedBelowY;
        }

        @Override
        public boolean isPassable(int x, int y, int z) {
            return x >= 1 && x <= 3 && y >= 1 && y <= 3 && z >= 1 && z <= 3;
        }

        @Override
        public boolean isLeaky(int x, int y, int z) {
            return z == 0 && x == 2 && leakyY.contains((long) y);
        }

        @Override
        public boolean isLoaded(int x, int y, int z) {
            return y < loadedBelowY;
        }
    }
}
