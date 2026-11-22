package com.bringtolife.sim;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record GasMixture(Map<Gas, Double> amounts) {

    public GasMixture {
        EnumMap<Gas, Double> copy = new EnumMap<>(Gas.class);
        double total = 0.0;
        for (Map.Entry<Gas, Double> entry : amounts.entrySet()) {
            double amount = entry.getValue();
            if (!Double.isFinite(amount) || amount < 0.0) {
                throw new IllegalArgumentException(entry.getKey().label() + " amount must be finite and non-negative");
            }
            copy.put(entry.getKey(), amount);
            total += amount;
        }
        if (total <= 0.0) {
            throw new IllegalArgumentException("a gas mixture must contain a positive amount of gas");
        }
        amounts = Collections.unmodifiableMap(copy);
    }

    public static GasMixture of(Gas gas, double moles) {
        return new GasMixture(Map.of(gas, moles));
    }

    public double moles(Gas gas) {
        return amounts.getOrDefault(gas, 0.0);
    }

    public double totalMoles() {
        double total = 0.0;
        for (double amount : amounts.values()) {
            total += amount;
        }
        return total;
    }

    public double moleFraction(Gas gas) {
        return moles(gas) / totalMoles();
    }

    public double massKg() {
        double mass = 0.0;
        for (Map.Entry<Gas, Double> entry : amounts.entrySet()) {
            mass += entry.getKey().molarMassKgPerMol() * entry.getValue();
        }
        return mass;
    }

    public GasMixture withChange(Map<Gas, Double> changes) {
        EnumMap<Gas, Double> updated = new EnumMap<>(Gas.class);
        updated.putAll(amounts);
        for (Map.Entry<Gas, Double> entry : changes.entrySet()) {
            updated.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
        updated.values().removeIf(amount -> amount == 0.0);
        if (updated.isEmpty()) {
            throw new IllegalArgumentException("removing every gas leaves no air");
        }
        return new GasMixture(updated);
    }

    public GasMixture blendedWith(GasMixture other, double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be between 0 and 1");
        }
        EnumMap<Gas, Double> blended = new EnumMap<>(Gas.class);
        for (Gas gas : Gas.values()) {
            double amount = (1.0 - fraction) * moles(gas) + fraction * other.moles(gas);
            if (amount > 0.0) {
                blended.put(gas, amount);
            }
        }
        return new GasMixture(blended);
    }
}
