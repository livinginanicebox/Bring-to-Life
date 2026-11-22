package com.bringtolife.sim;

public enum Gas {
    OXYGEN("oxygen", 2.0 * Elements.OXYGEN),
    NITROGEN("nitrogen", 2.0 * Elements.NITROGEN),
    ARGON("argon", Elements.ARGON),
    CARBON_DIOXIDE("carbon dioxide", Elements.CARBON + 2.0 * Elements.OXYGEN);

    private final String label;
    private final double molarMassKgPerMol;

    Gas(String label, double molarMassKgPerMol) {
        this.label = label;
        this.molarMassKgPerMol = molarMassKgPerMol;
    }

    public String label() {
        return label;
    }

    public double molarMassKgPerMol() {
        return molarMassKgPerMol;
    }
}
