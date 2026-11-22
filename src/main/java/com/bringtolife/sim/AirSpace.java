package com.bringtolife.sim;

import java.util.EnumMap;
import java.util.Map;

public record AirSpace(double volumeM3, GasMixture mixture, double airChangesPerHour) {

    public AirSpace {
        if (!Double.isFinite(volumeM3) || volumeM3 <= 0.0) {
            throw new IllegalArgumentException("volume must be finite and positive");
        }
        if (!Double.isFinite(airChangesPerHour) || airChangesPerHour < 0.0) {
            throw new IllegalArgumentException("air changes per hour must be finite and non-negative");
        }
    }

    public static AirSpace sealed(double volumeM3) {
        return new AirSpace(volumeM3, Air.freshAir(volumeM3), 0.0);
    }

    public static AirSpace ventilated(double volumeM3, double airChangesPerHour) {
        return new AirSpace(volumeM3, Air.freshAir(volumeM3), airChangesPerHour);
    }

    public double oxygenMoleFraction() {
        return mixture.moleFraction(Gas.OXYGEN);
    }

    public double carbonDioxideMoleFraction() {
        return mixture.moleFraction(Gas.CARBON_DIOXIDE);
    }

    public AirSpace resized(double newVolumeM3) {
        if (newVolumeM3 == volumeM3) {
            return this;
        }
        double scale = newVolumeM3 / volumeM3;
        EnumMap<Gas, Double> scaled = new EnumMap<>(Gas.class);
        for (Map.Entry<Gas, Double> entry : mixture.amounts().entrySet()) {
            scaled.put(entry.getKey(), entry.getValue() * scale);
        }
        return new AirSpace(newVolumeM3, new GasMixture(scaled), airChangesPerHour);
    }

    public AirSpace withAirChanges(double newAirChangesPerHour) {
        return new AirSpace(volumeM3, mixture, newAirChangesPerHour);
    }

    public AirSpace ventilate(double dtS) {
        if (airChangesPerHour == 0.0 || dtS == 0.0) {
            return this;
        }
        if (!Double.isFinite(dtS) || dtS < 0.0) {
            throw new IllegalArgumentException("time step must be finite and non-negative");
        }
        double exchanged = 1.0 - Math.exp(-airChangesPerHour / 3600.0 * dtS);
        return new AirSpace(volumeM3, mixture.blendedWith(Air.freshAir(volumeM3), exchanged), airChangesPerHour);
    }

    public BurnResult burn(Fuel fuel, double requestedKg, double exposedAreaM2, double dtS) {
        if (!Double.isFinite(requestedKg) || requestedKg < 0.0) {
            throw new IllegalArgumentException("fuel mass must be finite and non-negative");
        }
        if (!Double.isFinite(exposedAreaM2) || exposedAreaM2 <= 0.0) {
            throw new IllegalArgumentException("exposed area must be finite and positive");
        }
        if (!Double.isFinite(dtS) || dtS <= 0.0) {
            throw new IllegalArgumentException("time step must be finite and positive");
        }

        double oxygenBefore = oxygenMoleFraction();
        if (oxygenBefore <= fuel.flamingOxygenMoleFraction()) {
            return new BurnResult(this, Burn.none(oxygenBefore, true));
        }
        if (requestedKg == 0.0) {
            return new BurnResult(this, Burn.none(oxygenBefore, false));
        }

        double oxygenPerMol = fuel.oxygenDemandMolPerMol();
        double carbonDioxidePerMol = fuel.carbonDioxideMolPerMol();
        double gasMolesPerMol = carbonDioxidePerMol - oxygenPerMol;
        double limit = fuel.flamingOxygenMoleFraction();
        double usableOxygen = mixture.moles(Gas.OXYGEN) - limit * mixture.totalMoles();
        double ceilingMol = usableOxygen / (oxygenPerMol + limit * gasMolesPerMol);
        double requestedMol = requestedKg / fuel.molarMassKgPerMol();
        double burnedMol = Math.min(requestedMol, Math.max(0.0, ceilingMol));

        double burnedKg = burnedMol * fuel.molarMassKgPerMol();
        double massFlux = burnedKg / (exposedAreaM2 * dtS);
        if (massFlux < fuel.criticalMassFluxKgPerM2S()) {
            return new BurnResult(this, Burn.none(oxygenBefore, true));
        }

        double oxygenConsumed = burnedMol * oxygenPerMol;
        double carbonDioxideProduced = burnedMol * carbonDioxidePerMol;
        EnumMap<Gas, Double> change = new EnumMap<>(Gas.class);
        change.put(Gas.OXYGEN, -oxygenConsumed);
        change.put(Gas.CARBON_DIOXIDE, carbonDioxideProduced);
        GasMixture after = mixture.withChange(change);

        Burn burn = new Burn(
                burnedKg,
                oxygenConsumed,
                carbonDioxideProduced,
                burnedKg * fuel.waterMassKgPerKg(),
                burnedKg * fuel.lowerHeatingValueJPerKg(),
                massFlux,
                after.moleFraction(Gas.OXYGEN),
                burnedMol < requestedMol,
                false
        );
        return new BurnResult(new AirSpace(volumeM3, after, airChangesPerHour), burn);
    }
}
