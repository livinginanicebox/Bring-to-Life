package com.bringtolife.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FireBed {

    public static final double MAX_EXPOSED_AREA_M2 = 1.0;

    private final EnumMap<Fuel, Double> massKg = new EnumMap<>(Fuel.class);

    public void add(Fuel fuel, double amountKg) {
        if (!Double.isFinite(amountKg) || amountKg <= 0.0) {
            throw new IllegalArgumentException("added fuel must be finite and positive");
        }
        massKg.merge(fuel, amountKg, Double::sum);
    }

    public double massKg(Fuel fuel) {
        return massKg.getOrDefault(fuel, 0.0);
    }

    public double totalMassKg() {
        double total = 0.0;
        for (double mass : massKg.values()) {
            total += mass;
        }
        return total;
    }

    public boolean isEmpty() {
        return totalMassKg() <= 0.0;
    }

    public Map<Fuel, Double> contents() {
        return Collections.unmodifiableMap(massKg);
    }

    public Fuel dominantFuel() {
        Fuel dominant = Fuel.LOG;
        double most = -1.0;
        for (Map.Entry<Fuel, Double> entry : massKg.entrySet()) {
            if (entry.getValue() > most) {
                most = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return dominant;
    }

    public double exposedAreaM2() {
        double area = 0.0;
        for (Map.Entry<Fuel, Double> entry : massKg.entrySet()) {
            area += entry.getKey().exposedAreaM2(entry.getValue());
        }
        return Math.min(area, MAX_EXPOSED_AREA_M2);
    }

    public double burningRateKgPerS() {
        double rate = 0.0;
        for (Map.Entry<Fuel, Double> entry : massKg.entrySet()) {
            rate += entry.getKey().burningRateKgPerS(entry.getKey().exposedAreaM2(entry.getValue()));
        }
        double cap = Fuel.LOG.massFluxKgPerM2S() * MAX_EXPOSED_AREA_M2;
        return Math.min(rate, cap);
    }

    public double consume(double amountKg) {
        if (!Double.isFinite(amountKg) || amountKg < 0.0) {
            throw new IllegalArgumentException("consumed fuel must be finite and non-negative");
        }
        double totalArea = 0.0;
        for (Map.Entry<Fuel, Double> entry : massKg.entrySet()) {
            totalArea += entry.getKey().exposedAreaM2(entry.getValue());
        }
        if (totalArea <= 0.0) {
            return 0.0;
        }
        double remaining = amountKg;
        double consumed = 0.0;
        List<Map.Entry<Fuel, Double>> entries = new ArrayList<>(massKg.entrySet());
        for (Map.Entry<Fuel, Double> entry : entries) {
            Fuel fuel = entry.getKey();
            double share = fuel.exposedAreaM2(entry.getValue()) / totalArea;
            double take = Math.min(entry.getValue(), amountKg * share);
            if (take <= 0.0) {
                continue;
            }
            double left = entry.getValue() - take;
            if (left <= 0.0) {
                massKg.remove(fuel);
            } else {
                massKg.put(fuel, left);
            }
            remaining -= take;
            consumed += take;
        }
        if (remaining > 0.0 && !massKg.isEmpty()) {
            consumed += consume(remaining);
        }
        return consumed;
    }
}
