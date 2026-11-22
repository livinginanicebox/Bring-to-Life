from __future__ import annotations

from math import isfinite

from .gases import Gas, GasMixture

STANDARD_ATMOSPHERIC_PRESSURE_PA = 101_325.0
STANDARD_TEMPERATURE_K = 293.15
MOLAR_GAS_CONSTANT_J_PER_MOL_K = 8.314462618

DRY_AIR_MOLE_FRACTIONS = {
    Gas.NITROGEN: 0.780840,
    Gas.OXYGEN: 0.209476,
    Gas.ARGON: 0.009340,
    Gas.CARBON_DIOXIDE: 0.000420,
}


def air_moles(volume_m3: float, temperature_k: float, pressure_pa: float) -> float:
    if not isfinite(volume_m3) or volume_m3 <= 0.0:
        raise ValueError("volume must be finite and positive")
    if not isfinite(temperature_k) or temperature_k <= 0.0:
        raise ValueError("temperature must be finite and positive")
    if not isfinite(pressure_pa) or pressure_pa <= 0.0:
        raise ValueError("pressure must be finite and positive")
    return pressure_pa * volume_m3 / (MOLAR_GAS_CONSTANT_J_PER_MOL_K * temperature_k)


def fresh_air(
    volume_m3: float,
    temperature_k: float = STANDARD_TEMPERATURE_K,
    pressure_pa: float = STANDARD_ATMOSPHERIC_PRESSURE_PA,
) -> GasMixture:
    total = air_moles(volume_m3, temperature_k, pressure_pa)
    share = sum(DRY_AIR_MOLE_FRACTIONS.values())
    return GasMixture(
        {gas: total * fraction / share for gas, fraction in DRY_AIR_MOLE_FRACTIONS.items()}
    )
