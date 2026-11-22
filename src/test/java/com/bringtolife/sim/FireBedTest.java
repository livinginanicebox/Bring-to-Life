package com.bringtolife.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireBedTest {

    private static final double AREA_TOLERANCE = 1e-9;

    @Test
    void exposedAreaFollowsPieceGeometry() {
        FireBed bed = new FireBed();
        bed.add(Fuel.LOG, 5.0);
        assertEquals(5.0 / (600.0 * 0.10), bed.exposedAreaM2(), AREA_TOLERANCE);

        FireBed planks = new FireBed();
        planks.add(Fuel.PLANK, 5.0);
        assertEquals(5.0 / (600.0 * 0.02), planks.exposedAreaM2(), AREA_TOLERANCE);
        assertEquals(5.0, planks.exposedAreaM2() / bed.exposedAreaM2(), 1e-9);
    }

    @Test
    void exposedAreaIsCappedByTheHearthFootprint() {
        FireBed bed = new FireBed();
        bed.add(Fuel.PLANK, 40.0);
        assertTrue(40.0 / (600.0 * 0.02) > FireBed.MAX_EXPOSED_AREA_M2);
        assertEquals(FireBed.MAX_EXPOSED_AREA_M2, bed.exposedAreaM2(), AREA_TOLERANCE);
    }

    @Test
    void burnRateFollowsExposedAreaNotStoredMass() {
        FireBed oneLog = new FireBed();
        oneLog.add(Fuel.LOG, 5.0);
        FireBed twoLogs = new FireBed();
        twoLogs.add(Fuel.LOG, 5.0);
        twoLogs.add(Fuel.LOG, 5.0);

        assertEquals(Fuel.LOG.massFluxKgPerM2S() * oneLog.exposedAreaM2(), oneLog.burningRateKgPerS(), 1e-12);
        assertEquals(2.0, twoLogs.burningRateKgPerS() / oneLog.burningRateKgPerS(), 1e-9);
    }

    @Test
    void thinPiecesBurnThroughFasterThanThickOnesOfTheSameMass() {
        FireBed logs = new FireBed();
        logs.add(Fuel.LOG, 5.0);
        FireBed planks = new FireBed();
        planks.add(Fuel.PLANK, 5.0);

        double logSeconds = 5.0 / logs.burningRateKgPerS();
        double plankSeconds = 5.0 / planks.burningRateKgPerS();
        assertEquals(5.0, logSeconds / plankSeconds, 1e-6);
        assertEquals(Fuel.LOG.burnThroughSeconds(), logSeconds, 1e-6);
    }

    @Test
    void burningRateStopsGrowingWhenTheBedIsSaturated() {
        FireBed bed = new FireBed();
        bed.add(Fuel.PLANK, 40.0);
        assertEquals(Fuel.LOG.massFluxKgPerM2S() * FireBed.MAX_EXPOSED_AREA_M2, bed.burningRateKgPerS(), 1e-12);
    }

    @Test
    void consumptionIsSharedByExposedArea() {
        FireBed bed = new FireBed();
        bed.add(Fuel.LOG, 5.0);
        bed.add(Fuel.STICK, 0.5);

        double logArea = Fuel.LOG.exposedAreaM2(5.0);
        double stickArea = Fuel.STICK.exposedAreaM2(0.5);
        double logShare = logArea / (logArea + stickArea);

        double consumed = bed.consume(0.1);
        assertEquals(0.1, consumed, 1e-12);
        assertEquals(5.0 - 0.1 * logShare, bed.massKg(Fuel.LOG), 1e-12);
        assertEquals(0.5 - 0.1 * (1.0 - logShare), bed.massKg(Fuel.STICK), 1e-12);
    }

    @Test
    void consumptionNeverExceedsStoredMass() {
        FireBed bed = new FireBed();
        bed.add(Fuel.LOG, 1.0);

        assertEquals(1.0, bed.consume(5.0), 1e-12);
        assertTrue(bed.isEmpty());
        assertEquals(0.0, bed.totalMassKg(), 0.0);
    }

    @Test
    void fullyBurnedFuelIsRemoved() {
        FireBed bed = new FireBed();
        bed.add(Fuel.STICK, 0.05);
        bed.consume(0.05);
        assertTrue(bed.isEmpty());
        assertFalse(bed.contents().containsKey(Fuel.STICK));
    }

    @Test
    void dominantFuelIsTheLargestByMass() {
        FireBed bed = new FireBed();
        bed.add(Fuel.STICK, 0.2);
        bed.add(Fuel.LOG, 5.0);
        assertEquals(Fuel.LOG, bed.dominantFuel());
    }

    @Test
    void anEmptyBedHasNoExposedAreaOrBurnRate() {
        FireBed bed = new FireBed();
        assertTrue(bed.isEmpty());
        assertEquals(0.0, bed.exposedAreaM2(), 0.0);
        assertEquals(0.0, bed.burningRateKgPerS(), 0.0);
        assertEquals(0.0, bed.consume(1.0), 0.0);
    }

    @Test
    void rejectsInvalidFuelAmounts() {
        FireBed bed = new FireBed();
        assertThrows(IllegalArgumentException.class, () -> bed.add(Fuel.LOG, 0.0));
        assertThrows(IllegalArgumentException.class, () -> bed.add(Fuel.LOG, -1.0));
        assertThrows(IllegalArgumentException.class, () -> bed.add(Fuel.LOG, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> bed.consume(-1.0));
    }

    @Test
    void pieceThicknessMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new Fuel(
                "sheet", 6, 10, 5,
                16_100_000.0, 600.0, 1.0e-5, 0.15, 4.0e-3, 0.0));
    }
}
