package com.bringtolife.sim;

public record Fuel(
        String name,
        int carbonAtoms,
        int hydrogenAtoms,
        int oxygenAtoms,
        double lowerHeatingValueJPerKg,
        double densityKgPerM3,
        double regressionRateMPerS,
        double flamingOxygenMoleFraction,
        double criticalMassFluxKgPerM2S
) {

    public Fuel {
        if (carbonAtoms < 0 || hydrogenAtoms < 0 || oxygenAtoms < 0) {
            throw new IllegalArgumentException("atom counts must be non-negative");
        }
        if (carbonAtoms + hydrogenAtoms + oxygenAtoms == 0) {
            throw new IllegalArgumentException("a fuel must contain at least one atom");
        }
        requirePositive(lowerHeatingValueJPerKg, "lower heating value");
        requirePositive(densityKgPerM3, "density");
        requirePositive(regressionRateMPerS, "surface regression rate");
        requirePositive(criticalMassFluxKgPerM2S, "critical mass flux");
        if (!(flamingOxygenMoleFraction > 0.0 && flamingOxygenMoleFraction < 1.0)) {
            throw new IllegalArgumentException("flaming oxygen limit must be a mole fraction");
        }
        if (!(oxygenDemandMolPerMol() > 0.0)) {
            throw new IllegalArgumentException("a fuel must consume oxygen when it burns");
        }
        if (ceilingDenominator() <= 0.0) {
            throw new IllegalArgumentException("fuel composition leaves no stable oxygen ceiling");
        }
    }

    public static final Fuel DRY_WOOD = new Fuel(
            "dry wood",
            6, 10, 5,
            16_100_000.0,
            600.0,
            1.0e-5,
            0.15,
            4.0e-3
    );

    public double molarMassKgPerMol() {
        return carbonAtoms * Elements.CARBON
                + hydrogenAtoms * Elements.HYDROGEN
                + oxygenAtoms * Elements.OXYGEN;
    }

    public double oxygenDemandMolPerMol() {
        return carbonAtoms + hydrogenAtoms / 4.0 - oxygenAtoms / 2.0;
    }

    public double carbonDioxideMolPerMol() {
        return carbonAtoms;
    }

    public double oxygenDemandMolPerKg() {
        return oxygenDemandMolPerMol() / molarMassKgPerMol();
    }

    public double carbonDioxideMolPerKg() {
        return carbonDioxideMolPerMol() / molarMassKgPerMol();
    }

    public double waterMassKgPerKg() {
        double waterMolarMass = 2.0 * Elements.HYDROGEN + Elements.OXYGEN;
        return (hydrogenAtoms / 2.0) * waterMolarMass / molarMassKgPerMol();
    }

    public double massFluxKgPerM2S() {
        return densityKgPerM3 * regressionRateMPerS;
    }

    public double burningRateKgPerS(double exposedAreaM2) {
        if (!Double.isFinite(exposedAreaM2) || exposedAreaM2 < 0.0) {
            throw new IllegalArgumentException("exposed area must be finite and non-negative");
        }
        return massFluxKgPerM2S() * exposedAreaM2;
    }

    double ceilingDenominator() {
        return oxygenDemandMolPerMol() + flamingOxygenMoleFraction * (carbonDioxideMolPerMol() - oxygenDemandMolPerMol());
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }
}
