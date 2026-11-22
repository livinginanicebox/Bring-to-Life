import pytest

from bring_to_life.fuel import DRY_WOOD, Fuel


def test_dry_wood_stoichiometry_is_computed_from_cellulose() -> None:
    assert DRY_WOOD.molar_mass_kg_per_mol == pytest.approx(0.16214, rel=1e-4)
    assert DRY_WOOD.oxygen_demand_mol_per_kg == pytest.approx(37.0, rel=2e-3)
    assert DRY_WOOD.carbon_dioxide_mol_per_kg == pytest.approx(37.0, rel=2e-3)
    assert DRY_WOOD.water_mass_kg_per_kg == pytest.approx(0.556, abs=5e-3)


def test_dry_wood_products_match_combustion_data() -> None:
    carbon_dioxide_kg = DRY_WOOD.carbon_dioxide_mol_per_kg * 0.044009

    assert carbon_dioxide_kg == pytest.approx(1.63, abs=0.01)


def test_stoichiometric_air_demand_matches_wood() -> None:
    oxygen_kg = DRY_WOOD.oxygen_demand_mol_per_kg * 0.031998
    air_kg = oxygen_kg / 0.23141

    assert air_kg == pytest.approx(5.1, abs=0.1)


def test_oxygen_demand_follows_the_general_combustion_formula() -> None:
    methane = Fuel("methane", 1, 4, 0, 55_500_000.0, 1.0, 1e-5)
    carbon = Fuel("carbon", 1, 0, 0, 32_800_000.0, 2000.0, 1e-5)

    assert methane.oxygen_demand_mol_per_mol == pytest.approx(2.0)
    assert carbon.oxygen_demand_mol_per_mol == pytest.approx(1.0)


def test_burning_rate_is_surface_driven() -> None:
    assert DRY_WOOD.burning_rate_kg_per_s(1.0) == pytest.approx(6.0e-3, rel=1e-12)
    assert DRY_WOOD.burning_rate_kg_per_s(2.0) == pytest.approx(
        2.0 * DRY_WOOD.burning_rate_kg_per_s(1.0)
    )
    assert DRY_WOOD.burning_rate_kg_per_s(0.0) == 0.0


def test_invalid_fuels_are_rejected() -> None:
    with pytest.raises(ValueError):
        Fuel("nothing", 0, 0, 0, 17_400_000.0, 600.0, 1e-5)
    with pytest.raises(ValueError):
        Fuel("oxidiser", 0, 0, 1, 17_400_000.0, 600.0, 1e-5)
    with pytest.raises(ValueError):
        Fuel("broken", 6, 10, 5, 0.0, 600.0, 1e-5)
    with pytest.raises(ValueError):
        DRY_WOOD.burning_rate_kg_per_s(-1.0)
