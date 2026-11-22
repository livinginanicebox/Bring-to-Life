package com.bringtolife.sim;

import java.util.EnumMap;
import java.util.Map;

public final class Air {
    public static final double STANDARD_PRESSURE_PA = 101_325.0;
    public static final double STANDARD_TEMPERATURE_K = 293.15;
    public static final double MOLAR_GAS_CONSTANT = 8.314462618;

    private static final Map<Gas, Double> DRY_AIR_MOLE_FRACTIONS = Map.of(
            Gas.NITROGEN, 0.780840,
            Gas.OXYGEN, 0.209476,
            Gas.ARGON, 0.009340,
            Gas.CARBON_DIOXIDE, 0.000420
    );

    private Air() {
    }

    public static double molesFor(double volumeM3) {
        return molesFor(volumeM3, STANDARD_TEMPERATURE_K, STANDARD_PRESSURE_PA);
    }

    public static double molesFor(double volumeM3, double temperatureK, double pressurePa) {
        if (!(volumeM3 > 0.0) || !Double.isFinite(volumeM3)) {
            throw new IllegalArgumentException("volume must be finite and positive");
        }
        if (!(temperatureK > 0.0) || !Double.isFinite(temperatureK)) {
            throw new IllegalArgumentException("temperature must be finite and positive");
        }
        if (!(pressurePa > 0.0) || !Double.isFinite(pressurePa)) {
            throw new IllegalArgumentException("pressure must be finite and positive");
        }
        return pressurePa * volumeM3 / (MOLAR_GAS_CONSTANT * temperatureK);
    }

    public static GasMixture freshAir(double volumeM3) {
        double total = molesFor(volumeM3);
        double share = 0.0;
        for (double fraction : DRY_AIR_MOLE_FRACTIONS.values()) {
            share += fraction;
        }
        EnumMap<Gas, Double> amounts = new EnumMap<>(Gas.class);
        for (Map.Entry<Gas, Double> entry : DRY_AIR_MOLE_FRACTIONS.entrySet()) {
            amounts.put(entry.getKey(), total * entry.getValue() / share);
        }
        return new GasMixture(amounts);
    }
}
