from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from enum import Enum
from math import isfinite

from .elements import (
    ATOMIC_MASS_ARGON_KG_PER_MOL,
    ATOMIC_MASS_CARBON_KG_PER_MOL,
    ATOMIC_MASS_NITROGEN_KG_PER_MOL,
    ATOMIC_MASS_OXYGEN_KG_PER_MOL,
)


class Gas(Enum):
    OXYGEN = ("oxygen", 2.0 * ATOMIC_MASS_OXYGEN_KG_PER_MOL)
    NITROGEN = ("nitrogen", 2.0 * ATOMIC_MASS_NITROGEN_KG_PER_MOL)
    ARGON = ("argon", ATOMIC_MASS_ARGON_KG_PER_MOL)
    CARBON_DIOXIDE = (
        "carbon dioxide",
        ATOMIC_MASS_CARBON_KG_PER_MOL + 2.0 * ATOMIC_MASS_OXYGEN_KG_PER_MOL,
    )

    def __init__(self, label: str, molar_mass_kg_per_mol: float) -> None:
        self.label = label
        self.molar_mass_kg_per_mol = molar_mass_kg_per_mol


@dataclass(frozen=True)
class GasMixture:
    amounts: Mapping[Gas, float]

    def __post_init__(self) -> None:
        object.__setattr__(self, "amounts", dict(self.amounts))
        for gas, amount in self.amounts.items():
            if not isinstance(gas, Gas):
                raise TypeError("gas mixture keys must be Gas values")
            if not isfinite(amount) or amount < 0.0:
                raise ValueError(f"{gas.label} amount must be finite and non-negative")
        if self.total_moles() <= 0.0:
            raise ValueError("a gas mixture must contain a positive amount of gas")

    def moles(self, gas: Gas) -> float:
        return self.amounts.get(gas, 0.0)

    def total_moles(self) -> float:
        return sum(self.amounts.values())

    def mole_fraction(self, gas: Gas) -> float:
        return self.moles(gas) / self.total_moles()

    def mass_kg(self) -> float:
        return sum(gas.molar_mass_kg_per_mol * amount for gas, amount in self.amounts.items())

    def partial_pressure_pa(self, gas: Gas, total_pressure_pa: float) -> float:
        return self.mole_fraction(gas) * total_pressure_pa

    def with_change(self, changes: Mapping[Gas, float]) -> GasMixture:
        amounts = dict(self.amounts)
        for gas, change in changes.items():
            amounts[gas] = amounts.get(gas, 0.0) + change
        return GasMixture(amounts)

    def blended_with(self, other: GasMixture, fraction: float) -> GasMixture:
        if not isfinite(fraction) or not 0.0 <= fraction <= 1.0:
            raise ValueError("fraction must be between 0 and 1")
        gases = set(self.amounts) | set(other.amounts)
        return GasMixture(
            {gas: (1.0 - fraction) * self.moles(gas) + fraction * other.moles(gas) for gas in gases}
        )
