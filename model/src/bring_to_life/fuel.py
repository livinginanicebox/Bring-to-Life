from __future__ import annotations

from dataclasses import dataclass
from math import isfinite

from .elements import (
    ATOMIC_MASS_CARBON_KG_PER_MOL,
    ATOMIC_MASS_HYDROGEN_KG_PER_MOL,
    ATOMIC_MASS_OXYGEN_KG_PER_MOL,
)

WATER_MOLAR_MASS_KG_PER_MOL = 2.0 * ATOMIC_MASS_HYDROGEN_KG_PER_MOL + ATOMIC_MASS_OXYGEN_KG_PER_MOL


@dataclass(frozen=True)
class Fuel:
    name: str
    carbon_atoms: int
    hydrogen_atoms: int
    oxygen_atoms: int
    higher_heating_value_j_per_kg: float
    density_kg_per_m3: float
    surface_regression_rate_m_per_s: float

    def __post_init__(self) -> None:
        atoms = (self.carbon_atoms, self.hydrogen_atoms, self.oxygen_atoms)
        if any(not isinstance(count, int) or count < 0 for count in atoms):
            raise ValueError("atom counts must be non-negative integers")
        if sum(atoms) == 0:
            raise ValueError("a fuel must contain at least one atom")
        for value, label in (
            (self.higher_heating_value_j_per_kg, "higher heating value"),
            (self.density_kg_per_m3, "density"),
            (self.surface_regression_rate_m_per_s, "surface regression rate"),
        ):
            if not isfinite(value) or value <= 0.0:
                raise ValueError(f"{label} must be finite and positive")
        if self.oxygen_demand_mol_per_mol <= 0.0:
            raise ValueError("a fuel must consume oxygen when it burns")

    @property
    def molar_mass_kg_per_mol(self) -> float:
        return (
            self.carbon_atoms * ATOMIC_MASS_CARBON_KG_PER_MOL
            + self.hydrogen_atoms * ATOMIC_MASS_HYDROGEN_KG_PER_MOL
            + self.oxygen_atoms * ATOMIC_MASS_OXYGEN_KG_PER_MOL
        )

    @property
    def oxygen_demand_mol_per_mol(self) -> float:
        return self.carbon_atoms + self.hydrogen_atoms / 4.0 - self.oxygen_atoms / 2.0

    @property
    def carbon_dioxide_mol_per_mol(self) -> float:
        return float(self.carbon_atoms)

    @property
    def oxygen_demand_mol_per_kg(self) -> float:
        return self.oxygen_demand_mol_per_mol / self.molar_mass_kg_per_mol

    @property
    def carbon_dioxide_mol_per_kg(self) -> float:
        return self.carbon_dioxide_mol_per_mol / self.molar_mass_kg_per_mol

    @property
    def water_mass_kg_per_kg(self) -> float:
        water_mol_per_mol = self.hydrogen_atoms / 2.0
        return water_mol_per_mol * WATER_MOLAR_MASS_KG_PER_MOL / self.molar_mass_kg_per_mol

    def burning_rate_kg_per_s(self, exposed_area_m2: float) -> float:
        if not isfinite(exposed_area_m2) or exposed_area_m2 < 0.0:
            raise ValueError("exposed area must be finite and non-negative")
        return exposed_area_m2 * self.density_kg_per_m3 * self.surface_regression_rate_m_per_s


DRY_WOOD = Fuel(
    name="dry wood",
    carbon_atoms=6,
    hydrogen_atoms=10,
    oxygen_atoms=5,
    higher_heating_value_j_per_kg=17_400_000.0,
    density_kg_per_m3=600.0,
    surface_regression_rate_m_per_s=1.0e-5,
)
