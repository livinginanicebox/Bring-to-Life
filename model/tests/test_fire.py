import math

import pytest

from bring_to_life.fire import LIMITING_OXYGEN_MOLE_FRACTION, AirSpace
from bring_to_life.fuel import DRY_WOOD
from bring_to_life.gases import Gas


def space_with_oxygen_fraction(volume_m3: float, oxygen_fraction: float) -> AirSpace:
    base = AirSpace.sealed(volume_m3)
    displaced = base.mixture.moles(Gas.OXYGEN) - oxygen_fraction * base.mixture.total_moles()
    mixture = base.mixture.with_change({Gas.OXYGEN: -displaced, Gas.NITROGEN: displaced})
    return AirSpace(volume_m3=volume_m3, mixture=mixture)


def test_sealed_space_begins_as_fresh_air() -> None:
    space = AirSpace.sealed(27.0)

    assert space.oxygen_mole_fraction() == pytest.approx(0.2095, abs=1e-3)
    assert space.carbon_dioxide_mole_fraction() == pytest.approx(0.00042, abs=1e-4)
    assert space.air_changes_per_hour == 0.0


def test_burning_consumes_oxygen_and_releases_products() -> None:
    space = AirSpace.sealed(1000.0)

    burned, outcome = space.burn(DRY_WOOD, 0.005)

    assert outcome.fuel_kg == pytest.approx(0.005)
    assert outcome.oxygen_consumed_mol == pytest.approx(
        0.005 * DRY_WOOD.oxygen_demand_mol_per_kg
    )
    assert outcome.carbon_dioxide_produced_mol == pytest.approx(
        0.005 * DRY_WOOD.carbon_dioxide_mol_per_kg
    )
    assert outcome.water_kg == pytest.approx(0.005 * DRY_WOOD.water_mass_kg_per_kg)
    assert outcome.heat_j == pytest.approx(0.005 * DRY_WOOD.higher_heating_value_j_per_kg)
    assert burned.mixture.moles(Gas.OXYGEN) == pytest.approx(
        space.mixture.moles(Gas.OXYGEN) - outcome.oxygen_consumed_mol
    )
    assert not outcome.limited_by_oxygen
    assert not outcome.extinguished


def test_no_fuel_means_no_combustion() -> None:
    space = AirSpace.sealed(27.0)

    burned, outcome = space.burn(DRY_WOOD, 0.0)

    assert burned == space
    assert outcome.fuel_kg == 0.0
    assert outcome.heat_j == 0.0
    assert not outcome.extinguished
    assert not outcome.limited_by_oxygen


def test_gas_mass_is_conserved_apart_from_the_water_left_behind() -> None:
    space = AirSpace.sealed(6.0)

    burned, outcome = space.burn(DRY_WOOD, 0.4)

    mass_gain = burned.mixture.mass_kg() - space.mixture.mass_kg()
    assert mass_gain == pytest.approx(outcome.fuel_kg - outcome.water_kg, rel=1e-12)


def test_sealed_space_cannot_burn_past_the_limiting_oxygen_fraction() -> None:
    space = AirSpace.sealed(27.0)

    burned, outcome = space.burn(DRY_WOOD, 100.0)

    assert outcome.limited_by_oxygen
    assert not outcome.extinguished
    assert outcome.fuel_kg == pytest.approx(1.5, abs=0.01)
    assert burned.oxygen_mole_fraction() == pytest.approx(LIMITING_OXYGEN_MOLE_FRACTION, rel=1e-9)
    assert burned.carbon_dioxide_mole_fraction() == pytest.approx(0.05, abs=0.002)


def test_fire_is_extinguished_below_the_limiting_oxygen_fraction() -> None:
    space = space_with_oxygen_fraction(27.0, 0.155)

    burned, outcome = space.burn(DRY_WOOD, 1.0)

    assert outcome.extinguished
    assert outcome.fuel_kg == 0.0
    assert outcome.heat_j == 0.0
    assert burned == space


def test_a_sealed_fire_burns_out_and_leaves_the_room_unbreathable() -> None:
    space = AirSpace.sealed(27.0)
    fuel_rate_kg_per_s = DRY_WOOD.burning_rate_kg_per_s(1.0)
    time_step_s = 1.0

    elapsed_s = 0.0
    fuel_burned_kg = 0.0
    while elapsed_s < 900.0:
        space, outcome = space.burn(DRY_WOOD, fuel_rate_kg_per_s * time_step_s)
        if outcome.extinguished:
            break
        elapsed_s += time_step_s
        fuel_burned_kg += outcome.fuel_kg

    assert elapsed_s == pytest.approx(250.0, abs=2.0)
    assert fuel_burned_kg == pytest.approx(1.5, abs=0.01)
    assert space.oxygen_mole_fraction() == pytest.approx(LIMITING_OXYGEN_MOLE_FRACTION, rel=1e-9)
    assert space.carbon_dioxide_mole_fraction() == pytest.approx(0.05, abs=0.002)


def test_ventilation_pulls_the_room_back_toward_outdoor_air() -> None:
    space = space_with_oxygen_fraction(27.0, 0.16)
    ventilated = AirSpace(
        volume_m3=space.volume_m3,
        mixture=space.mixture,
        air_changes_per_hour=1.0,
    )

    after_one_hour = ventilated.ventilate(3600.0)

    outdoor_fraction = AirSpace.sealed(27.0).oxygen_mole_fraction()
    expected = outdoor_fraction + (0.16 - outdoor_fraction) / math.e
    assert after_one_hour.oxygen_mole_fraction() == pytest.approx(expected, abs=1e-5)


def test_a_ventilated_fire_keeps_burning() -> None:
    space = AirSpace.ventilated(27.0, air_changes_per_hour=30.0)
    fuel_rate_kg_per_s = DRY_WOOD.burning_rate_kg_per_s(1.0)
    time_step_s = 1.0

    for _ in range(600):
        space = space.ventilate(time_step_s)
        space, outcome = space.burn(DRY_WOOD, fuel_rate_kg_per_s * time_step_s)
        assert not outcome.extinguished

    assert space.oxygen_mole_fraction() == pytest.approx(0.186, abs=0.005)
    assert space.carbon_dioxide_mole_fraction() == pytest.approx(0.024, abs=0.003)


def test_invalid_requests_are_rejected() -> None:
    space = AirSpace.sealed(27.0)

    with pytest.raises(ValueError):
        space.burn(DRY_WOOD, -0.1)
    with pytest.raises(ValueError):
        space.ventilate(-1.0)
    with pytest.raises(ValueError):
        AirSpace(volume_m3=0.0, mixture=space.mixture)
    with pytest.raises(ValueError):
        AirSpace(volume_m3=27.0, mixture=space.mixture, air_changes_per_hour=-1.0)


def test_burn_down_does_not_depend_on_tick_length() -> None:
    def burn_down(time_step_s: float) -> tuple[float, float]:
        space = AirSpace.sealed(27.0)
        fuel_kg = 0.0
        for _ in range(int(900.0 / time_step_s) + 1):
            space, outcome = space.burn(
                DRY_WOOD, DRY_WOOD.burning_rate_kg_per_s(1.0) * time_step_s
            )
            if outcome.extinguished:
                break
            fuel_kg += outcome.fuel_kg
        return fuel_kg, space.oxygen_mole_fraction()

    coarse_fuel, coarse_oxygen = burn_down(1.0)
    fine_fuel, fine_oxygen = burn_down(0.05)

    assert fine_fuel == pytest.approx(coarse_fuel, rel=1e-9)
    assert fine_oxygen == pytest.approx(coarse_oxygen, abs=1e-9)
