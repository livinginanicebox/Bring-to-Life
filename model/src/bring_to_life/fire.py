from __future__ import annotations

from dataclasses import dataclass, replace
from math import exp, isfinite

from .air import STANDARD_ATMOSPHERIC_PRESSURE_PA, STANDARD_TEMPERATURE_K, fresh_air
from .fuel import Fuel
from .gases import Gas, GasMixture

LIMITING_OXYGEN_MOLE_FRACTION = 0.16


@dataclass(frozen=True)
class Burn:
    fuel_kg: float
    oxygen_consumed_mol: float
    carbon_dioxide_produced_mol: float
    water_kg: float
    heat_j: float
    oxygen_mole_fraction_after: float
    limited_by_oxygen: bool
    extinguished: bool


@dataclass(frozen=True)
class AirSpace:
    volume_m3: float
    mixture: GasMixture
    air_changes_per_hour: float = 0.0
    temperature_k: float = STANDARD_TEMPERATURE_K
    pressure_pa: float = STANDARD_ATMOSPHERIC_PRESSURE_PA

    def __post_init__(self) -> None:
        if not isfinite(self.volume_m3) or self.volume_m3 <= 0.0:
            raise ValueError("volume must be finite and positive")
        if not isfinite(self.temperature_k) or self.temperature_k <= 0.0:
            raise ValueError("temperature must be finite and positive")
        if not isfinite(self.pressure_pa) or self.pressure_pa <= 0.0:
            raise ValueError("pressure must be finite and positive")
        if not isfinite(self.air_changes_per_hour) or self.air_changes_per_hour < 0.0:
            raise ValueError("air changes per hour must be finite and non-negative")

    @classmethod
    def sealed(
        cls,
        volume_m3: float,
        temperature_k: float = STANDARD_TEMPERATURE_K,
        pressure_pa: float = STANDARD_ATMOSPHERIC_PRESSURE_PA,
    ) -> AirSpace:
        return cls(
            volume_m3=volume_m3,
            mixture=fresh_air(volume_m3, temperature_k, pressure_pa),
            temperature_k=temperature_k,
            pressure_pa=pressure_pa,
        )

    @classmethod
    def ventilated(
        cls,
        volume_m3: float,
        air_changes_per_hour: float,
        temperature_k: float = STANDARD_TEMPERATURE_K,
        pressure_pa: float = STANDARD_ATMOSPHERIC_PRESSURE_PA,
    ) -> AirSpace:
        return cls(
            volume_m3=volume_m3,
            mixture=fresh_air(volume_m3, temperature_k, pressure_pa),
            air_changes_per_hour=air_changes_per_hour,
            temperature_k=temperature_k,
            pressure_pa=pressure_pa,
        )

    def oxygen_mole_fraction(self) -> float:
        return self.mixture.mole_fraction(Gas.OXYGEN)

    def carbon_dioxide_mole_fraction(self) -> float:
        return self.mixture.mole_fraction(Gas.CARBON_DIOXIDE)

    def ventilate(self, dt_s: float) -> AirSpace:
        if not isfinite(dt_s) or dt_s < 0.0:
            raise ValueError("time step must be finite and non-negative")
        if self.air_changes_per_hour == 0.0 or dt_s == 0.0:
            return self
        exchanged = 1.0 - exp(-self.air_changes_per_hour / 3600.0 * dt_s)
        outdoor = fresh_air(self.volume_m3, self.temperature_k, self.pressure_pa)
        return replace(self, mixture=self.mixture.blended_with(outdoor, exchanged))

    def burn(self, fuel: Fuel, fuel_kg: float) -> tuple[AirSpace, Burn]:
        if not isfinite(fuel_kg) or fuel_kg < 0.0:
            raise ValueError("fuel mass must be finite and non-negative")
        oxygen_before = self.oxygen_mole_fraction()
        starved = oxygen_before <= LIMITING_OXYGEN_MOLE_FRACTION
        if starved or fuel_kg == 0.0:
            return self, Burn(
                fuel_kg=0.0,
                oxygen_consumed_mol=0.0,
                carbon_dioxide_produced_mol=0.0,
                water_kg=0.0,
                heat_j=0.0,
                oxygen_mole_fraction_after=oxygen_before,
                limited_by_oxygen=False,
                extinguished=starved,
            )

        oxygen_per_mol = fuel.oxygen_demand_mol_per_mol
        carbon_dioxide_per_mol = fuel.carbon_dioxide_mol_per_mol
        gas_moles_per_mol = carbon_dioxide_per_mol - oxygen_per_mol
        usable_oxygen = (
            self.mixture.moles(Gas.OXYGEN)
            - LIMITING_OXYGEN_MOLE_FRACTION * self.mixture.total_moles()
        )
        fuel_mol_ceiling = usable_oxygen / (
            oxygen_per_mol + LIMITING_OXYGEN_MOLE_FRACTION * gas_moles_per_mol
        )
        requested_mol = fuel_kg / fuel.molar_mass_kg_per_mol
        burned_mol = min(requested_mol, fuel_mol_ceiling)

        burned_kg = burned_mol * fuel.molar_mass_kg_per_mol
        oxygen_consumed = burned_mol * oxygen_per_mol
        carbon_dioxide = burned_mol * carbon_dioxide_per_mol

        mixture = self.mixture.with_change(
            {
                Gas.OXYGEN: -oxygen_consumed,
                Gas.CARBON_DIOXIDE: carbon_dioxide,
            }
        )
        outcome = Burn(
            fuel_kg=burned_kg,
            oxygen_consumed_mol=oxygen_consumed,
            carbon_dioxide_produced_mol=carbon_dioxide,
            water_kg=burned_kg * fuel.water_mass_kg_per_kg,
            heat_j=burned_kg * fuel.higher_heating_value_j_per_kg,
            oxygen_mole_fraction_after=mixture.mole_fraction(Gas.OXYGEN),
            limited_by_oxygen=burned_mol < requested_mol,
            extinguished=False,
        )
        return replace(self, mixture=mixture), outcome
