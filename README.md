# Bring to Life

A realism-focused conversion of Minecraft: vanilla mechanics are replaced by systems that
follow real physics, chemistry, biology, geology and human behaviour, so that difficulty and
depth come from how the world works rather than from added meters or modifiers.

## Current state

There is no game code yet. This repository contains the project's first simulation core,
`model/`: a dependency-free reference implementation of one mechanic, with a test suite that
pins the physics down.

The Minecraft layer (blocks, tick loop, persistence, rendering) does not exist yet and is not
part of this change. This environment has no JVM or Minecraft toolchain, and Maven/Fabric
repositories and release binaries are unreachable, so a Fabric module could not have been
compiled or tested. Rather than commit unverified game code, this change implements the part
that can be proven correct: the model. It is plain arithmetic over immutable records, written
so it can be transliterated into the mod's Java core without redesign.

## The mechanic: fire is limited by the air it breathes

In Minecraft a fire burns anywhere, forever, and the room around it is unchanged. In reality
combustion is a reaction that consumes oxygen and produces carbon dioxide, water and heat, and
it stops when the oxygen runs out. A torch in a sealed stone room suffocates.

`model/` implements that causal chain:

- air is a conserved inventory of gas, with composition and mass, held by an enclosed volume;
- burning fuel consumes oxygen and produces carbon dioxide, water and heat in stoichiometric
  proportions fixed by the fuel's elemental composition;
- the burn rate is driven by how much fuel surface is exposed to the flame, and can never
  exceed what the oxygen present allows;
- flaming combustion stops below the limiting oxygen concentration, leaving the space
  oxygen-poor and carbon-dioxide-rich;
- openings exchange the enclosure's air with the outdoors at a given number of air changes per
  hour, which is what decides whether a fire can be sustained at all.

## Why this mechanic first

- **It turns air into a substance.** A conserved gas inventory in a volume is the foundation
  for ventilation, breathing, smelting, furnace draught, cave air and smoke later on. Those
  systems then share one model instead of each inventing its own resource.
- **One causal chain, no meters.** Nothing here is a status effect, a bar or a difficulty
  multiplier. The consequence (a fire that dies) follows from a mass balance.
- **No balancing knobs.** Every constant is a measured property: molar masses, air
  composition, cellulose combustion enthalpy, wood charring rate, the limiting oxygen
  concentration. There is no "burn speed" setting.
- **It is verifiable now.** The mechanic is arithmetic over state, so it can be tested exactly
  rather than play-tested by hope.

## The physics

### Stoichiometry

Dry wood is modelled as cellulose, `C6H10O5`, burning completely:

```
C6H10O5 + 6 O2 -> 6 CO2 + 5 H2O
```

Per kilogram of dry wood this fixes the chemistry with no free parameters:

| Quantity | Value |
| --- | --- |
| Oxygen demand | 37.0 mol O2 = 1.18 kg |
| Carbon dioxide | 37.0 mol CO2 = 1.63 kg |
| Water | 0.56 kg |
| Stoichiometric air | ≈ 5.1 kg |

Product masses are derived from the same atomic masses as the fuel, so gas mass is conserved
to machine precision: the gas in the room gains exactly the fuel mass minus the water that
condenses on the room's surfaces. The water a fire produces leaves the gas phase in this
model, which is why heat release uses the higher heating value (17.4 MJ/kg for cellulose)
rather than the lower one — the latent heat of the water is accounted for, not lost.

### Air

Dry air is 78.084% N2, 20.9476% O2, 0.934% Ar and 0.042% CO2 by mole (the published figures,
normalised in the model because they sum to slightly over one), treated as an ideal gas.
One cubic metre at 20 °C and 101.325 kPa holds 41.57 mol of air (1.204 kg), of which 8.71 mol
(0.279 kg) is oxygen. A sealed 3×3×3 room therefore contains 7.5 kg of oxygen.

### Burn rate

Solid fuel burns at its surface. With a wood density of 600 kg/m³ and a charring rate of
0.6 mm/min, one square metre of exposed wood consumes 6.0 g/s and releases ≈ 104 kW. Doubling
the exposed area doubles the rate: that is why kindling lights and logs do not, and why the
model takes exposed area instead of a per-block burn timer.

### The oxygen limit

Flaming combustion of wood cannot be sustained below roughly 15% oxygen; 16% is the usual
fire-safety figure for the minimum oxygen concentration that supports flaming combustion, and
it is the value used here. Above it a fire burns as fast as its surface allows or as fast as
the available oxygen allows, whichever is slower; at or below it, the flame goes out.

The oxygen ceiling is solved in closed form rather than searched. Burning `x` mol of fuel
removes `a·x` mol O2 and adds `b·x` mol CO2, so the oxygen mole fraction is a linear function of
`x` and the fuel mass at which the room reaches the limit follows directly. That is why a
sealed room always ends at exactly the limiting fraction, and why the outcome does not depend
on tick length.

### The enclosure

Air in a room is one well-mixed zone. Ventilation exchanges it with outdoor air at a given
number of air changes per hour, integrated exactly so that the result is independent of tick
length. A single zone is the standard room-fire assumption; it is deliberately simple, and it
is where a two-zone or CFD model would eventually replace this one.

## Verified behaviour

Results from the test suite, with 1 m² of burning face at 20 °C and 101.325 kPa:

| Scenario | Result |
| --- | --- |
| Sealed 27 m³ room | burns 1.50 kg of wood in 250 s, then dies |
| Energy released before extinction | 26 MJ |
| Water left behind | 0.83 kg |
| Air left behind | 16.0% O2, 5.0% CO2 |
| 5 air changes/hour | starved: pinned at 16.0% O2, burning 2.1 g/s instead of 6.0 g/s |
| ~14 air changes/hour | the fire is free-burning again; below this it is oxygen-limited |
| 30 air changes/hour | steady state 18.6% O2, 2.4% CO2 |
| 60 air changes/hour | steady state 19.8% O2, 1.2% CO2 |

The numbers that matter for play: a sealed room kills a fire in four minutes, and the air it
leaves behind is around 5% CO2, well past the concentration that is immediately dangerous to
life. Ventilation is not a comfort setting. A 104 kW fire needs about 3.4 room-volumes of air
per hour just to meet stoichiometry, and roughly four times that before the flame stops
starving; a crack under a door is not enough.

Because the model is a mass balance, the burn-down is tick-length independent to floating
point: a fire burns the same fuel and leaves the same air whether it is stepped at one second
or at twenty ticks per second.

## Using it

```python
from bring_to_life import AirSpace, DRY_WOOD

room = AirSpace.sealed(27.0)
fuel_rate = DRY_WOOD.burning_rate_kg_per_s(1.0)

for _ in range(20 * 300):
    room = room.ventilate(0.05)
    room, burn = room.burn(DRY_WOOD, fuel_rate * 0.05)
    if burn.extinguished:
        break
```

`burn` returns what happened (`fuel_kg`, `oxygen_consumed_mol`, `carbon_dioxide_produced_mol`,
`water_kg`, `heat_j`, `oxygen_mole_fraction_after`, `limited_by_oxygen`, `extinguished`) and the
resulting `AirSpace`. Both are immutable, so the tick loop carries one value and replaces it.

## What the mod layer must supply

The model deliberately knows nothing about Minecraft. To wire it up, the game layer has to:

- **Find enclosures.** Flood-fill passable cells from a fire; the enclosed cells times 1 m³
  give the volume. Caves, sealed builds and open terrain then come out naturally.
- **Measure leakage.** Openings and gaps become air changes per hour; a sealed room is 0.
- **Supply the burning area.** Exposed face area of the burning fuel, scaled by fuel
  definition. This is where "kindling versus log" lives.
- **Apply the heat.** `heat_j` goes to a thermal system that does not exist yet.
- **Apply the water.** `water_kg` is water that should appear on cold surfaces, or be lost to
  the fire's plume when ventilated.

## Deliberately not modelled yet

- **Carbon monoxide.** Real under-ventilated fires produce CO, which is what actually kills
  people. Its yield depends on the equivalence ratio inside the flame, which a single
  well-mixed zone cannot resolve, so it is not faked here.
- **Temperature.** Temperature is an input used for gas density, not a state. The thermal layer
  is the next system; the heat output above is its input. Until it exists, a fire that is
  oxygen-starved stays alight at the limiting fraction as long as air keeps arriving, where a
  real starved fire would eventually lose heat faster than it releases it.
- **Humidity.** Combustion water is returned as liquid rather than tracked as vapour, so there
  is no steam phase, no fog and no damp-air state yet.
- **Ignition, flame spread and smouldering.** Separate mechanics.
- **Multiple zones.** One well-mixed volume per enclosure; no stratification or pressure-driven
  flow, and ventilation is an imposed air-change rate rather than a consequence of buoyancy.

## Running the tests

```
cd model
python -m pytest
```

The model needs only the standard library; pytest is the sole test dependency.
