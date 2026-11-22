package com.bringtolife.sim;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

class AirSpaceTest {

    private static final double AREA = 1.0;
    private static final double STEP = 1.0;

    @Test
    void freshAirHoldsStandardComposition() {
        GasMixture air = Air.freshAir(27.0);

        assertEquals(1122.4, air.totalMoles(), 0.5);
        assertEquals(0.2094, air.moleFraction(Gas.OXYGEN), 5e-4);
        assertEquals(0.7808, air.moleFraction(Gas.NITROGEN), 5e-4);
        assertEquals(0.00042, air.moleFraction(Gas.CARBON_DIOXIDE), 1e-5);
        assertEquals(1.204, air.massKg() / 27.0, 0.003);
    }

    @Test
    void woodStoichiometryFollowsFromCellulose() {
        Fuel wood = Fuel.LOG;

        assertEquals(0.16214, wood.molarMassKgPerMol(), 1e-5);
        assertEquals(37.0, wood.oxygenDemandMolPerKg(), 0.1);
        assertEquals(1.63, wood.carbonDioxideMolPerKg() * 0.044009, 0.01);
        assertEquals(0.556, wood.waterMassKgPerKg(), 0.005);
        assertEquals(6.0e-3, wood.massFluxKgPerM2S(), 1e-12);
        assertEquals(5.1, wood.oxygenDemandMolPerKg() * 0.031998 / 0.23141, 0.1);
    }

    @Test
    void burningConsumesOxygenAndReleasesProducts() {
        AirSpace space = AirSpace.sealed(1000.0);

        BurnResult result = space.burn(Fuel.LOG, 0.005, AREA, STEP);
        Burn burn = result.burn();

        assertEquals(0.005, burn.fuelKg(), 1e-12);
        assertEquals(0.005 * Fuel.LOG.oxygenDemandMolPerKg(), burn.oxygenConsumedMol(), 1e-9);
        assertEquals(0.005 * Fuel.LOG.carbonDioxideMolPerKg(), burn.carbonDioxideProducedMol(), 1e-9);
        assertEquals(0.005 * Fuel.LOG.waterMassKgPerKg(), burn.waterKg(), 1e-12);
        assertEquals(0.005 * Fuel.LOG.lowerHeatingValueJPerKg(), burn.heatJ(), 1e-3);
        assertEquals(5.0e-3, burn.massFluxKgPerM2S(), 1e-12);
        assertFalse(burn.limitedByOxygen());
        assertFalse(burn.extinguished());
    }

    @Test
    void gasMassIsConservedApartFromTheWaterThatLeaves() {
        AirSpace space = AirSpace.sealed(6.0);

        BurnResult result = space.burn(Fuel.LOG, 0.4, AREA, STEP);
        double massGain = result.space().mixture().massKg() - space.mixture().massKg();

        assertEquals(result.burn().fuelKg() - result.burn().waterKg(), massGain, 1e-12);
    }

    @Test
    void aSealedFireBurnsOutAtTheFlamingOxygenLimit() {
        AirSpace space = AirSpace.sealed(27.0);
        double elapsed = 0.0;
        double fuelBurned = 0.0;

        while (elapsed < 1000.0) {
            BurnResult result = space.burn(Fuel.LOG, Fuel.LOG.burningRateKgPerS(AREA) * STEP, AREA, STEP);
            if (result.burn().extinguished()) {
                break;
            }
            space = result.space();
            fuelBurned += result.burn().fuelKg();
            elapsed += STEP;
        }

        assertEquals(300.0, elapsed, 2.0);
        assertEquals(1.80, fuelBurned, 0.01);
        assertEquals(0.15, space.oxygenMoleFraction(), 0.002);
        assertEquals(0.06, space.carbonDioxideMoleFraction(), 0.003);
    }

    @Test
    void noFuelBurnsBelowTheFlamingOxygenLimit() {
        AirSpace fresh = AirSpace.sealed(27.0);
        double displaced = fresh.mixture().moles(Gas.OXYGEN) - 0.14 * fresh.mixture().totalMoles();
        Map<Gas, Double> change = new EnumMap<>(Gas.class);
        change.put(Gas.OXYGEN, -displaced);
        change.put(Gas.NITROGEN, displaced);
        AirSpace depleted = new AirSpace(27.0, fresh.mixture().withChange(change), 0.0);

        BurnResult result = depleted.burn(Fuel.LOG, 1.0, AREA, STEP);

        assertTrue(result.burn().extinguished());
        assertEquals(0.0, result.burn().fuelKg());
        assertSame(depleted, result.space());
    }

    @Test
    void aFlameDiesWhenItsMassFluxFallsBelowTheCriticalValue() {
        AirSpace space = AirSpace.sealed(27.0);

        BurnResult result = space.burn(Fuel.LOG, 1.0e-5, AREA, STEP);

        assertTrue(result.burn().extinguished());
        assertEquals(0.0, result.burn().fuelKg());
        assertSame(space, result.space());
    }

    @Test
    void ventilationPullsTheRoomBackTowardOutdoorAir() {
        AirSpace fresh = AirSpace.sealed(27.0);
        double displaced = fresh.mixture().moles(Gas.OXYGEN) - 0.16 * fresh.mixture().totalMoles();
        Map<Gas, Double> change = new EnumMap<>(Gas.class);
        change.put(Gas.OXYGEN, -displaced);
        change.put(Gas.NITROGEN, displaced);
        AirSpace depleted = new AirSpace(27.0, fresh.mixture().withChange(change), 1.0);

        AirSpace afterHour = depleted.ventilate(3600.0);

        double outdoor = AirSpace.sealed(27.0).oxygenMoleFraction();
        assertEquals(outdoor + (0.16 - outdoor) / Math.E, afterHour.oxygenMoleFraction(), 1e-6);
    }

    @Test
    void aVentilatedFireKeepsBurning() {
        AirSpace space = AirSpace.ventilated(27.0, 30.0);
        double rate = Fuel.LOG.burningRateKgPerS(AREA);

        for (int step = 0; step < 600; step++) {
            space = space.ventilate(STEP);
            BurnResult result = space.burn(Fuel.LOG, rate * STEP, AREA, STEP);
            assertFalse(result.burn().extinguished());
            space = result.space();
        }

        double airMolesPerM3 = Air.freshAir(27.0).totalMoles() / 27.0;
        double inflowMolPerS = 30.0 / 3600.0 * 27.0 * airMolesPerM3;
        double demandMolPerS = rate * Fuel.LOG.oxygenDemandMolPerKg();
        double freshOxygen = AirSpace.sealed(27.0).oxygenMoleFraction();
        double steadyState = freshOxygen - demandMolPerS / inflowMolPerS;

        assertEquals(0.1858, steadyState, 1e-3);
        assertEquals(steadyState, space.oxygenMoleFraction(), 5e-4);
        assertTrue(space.carbonDioxideMoleFraction() > 0.0);
    }

    @Test
    void burnDownIsNearlyIndependentOfTickLength() {
        double coarse = fuelBurnedBeforeExtinction(1.0);
        double fine = fuelBurnedBeforeExtinction(0.05);

        assertEquals(coarse, fine, coarse * 0.005);
    }

    @Test
    void invalidRequestsAreRejected() {
        AirSpace space = AirSpace.sealed(27.0);

        assertThrows(IllegalArgumentException.class, () -> space.burn(Fuel.LOG, -1.0, AREA, STEP));
        assertThrows(IllegalArgumentException.class, () -> space.burn(Fuel.LOG, 1.0, 0.0, STEP));
        assertThrows(IllegalArgumentException.class, () -> space.burn(Fuel.LOG, 1.0, AREA, 0.0));
        assertThrows(IllegalArgumentException.class, () -> AirSpace.sealed(0.0));
        assertThrows(IllegalArgumentException.class, () -> AirSpace.ventilated(27.0, -1.0));
        assertThrows(IllegalArgumentException.class, () -> space.ventilate(-1.0));
        assertThrows(IllegalArgumentException.class, () -> Air.freshAir(-1.0));
    }

    private static double fuelBurnedBeforeExtinction(double step) {
        AirSpace space = AirSpace.sealed(27.0);
        double rate = Fuel.LOG.burningRateKgPerS(AREA);
        double burned = 0.0;
        for (int i = 0; i < (int) (1000.0 / step); i++) {
            BurnResult result = space.burn(Fuel.LOG, rate * step, AREA, step);
            if (result.burn().extinguished()) {
                return burned;
            }
            space = result.space();
            burned += result.burn().fuelKg();
        }
        throw new AssertionError("fire never went out");
    }
}
