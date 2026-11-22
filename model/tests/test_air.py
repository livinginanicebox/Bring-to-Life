import pytest

from bring_to_life.air import (
    MOLAR_GAS_CONSTANT_J_PER_MOL_K,
    STANDARD_ATMOSPHERIC_PRESSURE_PA,
    STANDARD_TEMPERATURE_K,
    air_moles,
    fresh_air,
)
from bring_to_life.gases import Gas, GasMixture


def test_fresh_air_holds_standard_composition() -> None:
    volume_m3 = 27.0
    mixture = fresh_air(volume_m3)

    expected_moles = (
        STANDARD_ATMOSPHERIC_PRESSURE_PA * volume_m3
        / (MOLAR_GAS_CONSTANT_J_PER_MOL_K * STANDARD_TEMPERATURE_K)
    )
    assert mixture.total_moles() == pytest.approx(expected_moles, rel=1e-12)
    assert mixture.mole_fraction(Gas.OXYGEN) == pytest.approx(0.2094, abs=5e-4)
    assert mixture.mole_fraction(Gas.NITROGEN) == pytest.approx(0.7808, abs=5e-4)
    assert mixture.mole_fraction(Gas.CARBON_DIOXIDE) == pytest.approx(0.00042, abs=1e-5)
    assert mixture.mass_kg() / volume_m3 == pytest.approx(1.204, rel=2e-3)


def test_air_volume_scales_molar_inventory() -> None:
    assert air_moles(54.0, STANDARD_TEMPERATURE_K, STANDARD_ATMOSPHERIC_PRESSURE_PA) == pytest.approx(
        2.0 * air_moles(27.0, STANDARD_TEMPERATURE_K, STANDARD_ATMOSPHERIC_PRESSURE_PA)
    )


def test_invalid_air_states_are_rejected() -> None:
    with pytest.raises(ValueError):
        air_moles(0.0, 293.15, 101_325.0)
    with pytest.raises(ValueError):
        air_moles(1.0, 0.0, 101_325.0)
    with pytest.raises(ValueError):
        air_moles(1.0, 293.15, 0.0)
    with pytest.raises(ValueError):
        fresh_air(-1.0)


def test_mixture_rejects_impossible_amounts() -> None:
    with pytest.raises(ValueError):
        GasMixture({Gas.OXYGEN: -1.0})
    with pytest.raises(ValueError):
        GasMixture({})
    with pytest.raises(ValueError):
        GasMixture({Gas.OXYGEN: 1.0}).with_change({Gas.OXYGEN: -2.0})


def test_mixture_reports_absent_gas_as_empty() -> None:
    mixture = GasMixture({Gas.OXYGEN: 1.0, Gas.NITROGEN: 3.0})

    assert mixture.moles(Gas.CARBON_DIOXIDE) == 0.0
    assert mixture.mole_fraction(Gas.CARBON_DIOXIDE) == 0.0


def test_blending_interpolates_between_mixtures() -> None:
    oxygen_only = GasMixture({Gas.OXYGEN: 1.0})
    nitrogen_only = GasMixture({Gas.NITROGEN: 3.0})

    blended = oxygen_only.blended_with(nitrogen_only, 0.5)

    assert blended.moles(Gas.OXYGEN) == pytest.approx(0.5)
    assert blended.moles(Gas.NITROGEN) == pytest.approx(1.5)
    with pytest.raises(ValueError):
        oxygen_only.blended_with(nitrogen_only, 1.5)
