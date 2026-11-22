package com.bringtolife.sim;

public record Burn(
        double fuelKg,
        double oxygenConsumedMol,
        double carbonDioxideProducedMol,
        double waterKg,
        double heatJ,
        double massFluxKgPerM2S,
        double oxygenMoleFractionAfter,
        boolean limitedByOxygen,
        boolean extinguished
) {

    public static Burn none(double oxygenMoleFraction, boolean extinguished) {
        return new Burn(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, oxygenMoleFraction, false, extinguished);
    }
}
