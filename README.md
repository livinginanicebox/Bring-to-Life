# Bring to Life

A realism-focused conversion of Minecraft: vanilla mechanics are replaced by systems that follow
real physics, chemistry, biology, geology and human behaviour, so that difficulty and depth come
from how the world works rather than from added meters or modifiers.

Fabric mod for Minecraft 1.20.1 (Java 17). Two source trees:

- `src/main/java/com/bringtolife/sim/` — the simulation core: pure Java records, no Minecraft
  imports, unit-tested directly.
- `src/main/java/com/bringtolife/game/` and `block/` — the Minecraft layer: the hearth block,
  enclosure detection, per-world persistence, particles and textures.

## The mechanic: fire is a physical object burning in an air supply

Vanilla fire burns anywhere, forever, and leaves the room around it unchanged. Here a fire is a
quantity of fuel with a surface, burning in a measured atmosphere.

### Fuel burns from its surface, not from its mass

Wood burns at a rate set by the fuel surface exposed to the flame, not by how much wood is in the
pile. The controlling quantity is the mass loss flux, measured at 2.5–5 g/m²·s for self-sustaining
timber fires (*critical mass loss rates for extinction vary from 2.5 to 5 g/m²·s*, review,
PMC9308566; Emberley et al., *Combustion and Flame* 2021, extinction below 4.0 g/m²·s; Bartlett et
al., 3.5 g/m²·s at 31 kW/m²). This mod uses 4.0 g/m²·s, the value at which a timber fire
self-extinguishes (that is, the value the sources agree on as the *survival threshold*).

Surface area is derived from the geometry of the fuel, not from a constant:

```
area = mass / (density × piece thickness)
rate = flux × area
```

with wood at 600 kg/m³ and pieces of 0.10 m (log), 0.02 m (plank) and 0.005 m (stick). The bed's
total exposed area is capped at 1.0 m² — the footprint of the hearth block — so piling in more fuel
than one block's surface can hold no longer increases the fire's power.

Consequences a player sees and feels:

| what you load | exposed area | release rate | how long it lasts |
|---|---|---|---|
| one log (5 kg) | 0.083 m² | 5.4 kW | 4 h 10 min |
| four planks (4 kg) | 0.33 m² | 21.5 kW | 50 min |
| twenty sticks (1 kg) | 0.33 m² | 21.5 kW | 12 min |
| eight logs (40 kg, hearth limit) | 0.67 m² | 43 kW | 4 h 10 min |

Burn-through time is `density × thickness / flux`, so it depends only on how thick the pieces are:
4 h 10 min for logs, 50 min for planks, 12 min for sticks, as long as the pile's surface stays under
the 1 m² cap. Adding more of the same fuel makes the fire hotter, not longer, until the hearth's
surface is saturated at 4 g/s.

A pile of thin wood is hot and short-lived; one thick log is a small fire that lasts the night.
Adding more fuel makes the fire wider, not longer, until the hearth's surface is saturated.

### The fire's demand is matched against the air

Burning consumes oxygen and produces carbon dioxide in the stoichiometric ratio of cellulose,
`C6H10O5 + 6 O2 -> 6 CO2 + 5 H2O`, with a lower heating value of 16.1 MJ/kg. Air is a conserved
inventory of moles in the enclosing volume: 27 m³ of air holds 235 mol of oxygen, enough for 6.3 kg
of wood in total, of which only the 1.8 kg above the 15 % floor can be reached. Flaming combustion stops below an oxygen mole fraction of 0.15 (flaming/pyrolysis
transition measured at 4–15 % O₂, Richter, *Oxygen's effect on wood charring rate*), and a burn that
would deliver less than the critical flux is extinguished rather than sustained: a fire whose fuel
is too sparse to keep up 4.0 g/m²·s dies, which is why small isolated pieces cannot burn alone.

Ventilation is not a switch. Openings are measured from the enclosure's actual geometry: each
passable, non-solid block on the boundary contributes 0.02 m² of leakage area, and the resulting
air change rate is `Cd × area × velocity × 3600 / volume` with Cd 0.6 and a 1 m/s stack velocity.
One closed door in a 27 m³ room is 1.6 air changes per hour, two are 3.2.

Three outcomes, all from the same arithmetic:

- **sealed 27 m³ room, one log (5.4 kW)**: oxygen falls to 0.15 in 1½ hours, having burned 1.8 kg of
  the log, and the fire suffocates with 3.2 kg of wood still in the hearth;
- **sealed room, eight logs (43 kW)**: the same 1.8 kg of wood is gone in 11 minutes — the fire is
  over before the evening starts;
- **closed door (1.6 ACH)**: leakage supplies ~2.8 g/s of wood's worth of oxygen. A single log
  (0.33 g/s) burns comfortably; a bed packed with planks (4 g/s) outruns the door and suffocates;
- **open door or a hole in the wall**: treated as unbounded air, the fire burns until the fuel is
  gone.

### Feedback is the world, not a bar

The hearth block's appearance and particles are driven by its actual heat release rate: the
texture gains glowing embers as it heats up (four states), flame particles scale from a single
flame to a roaring fire with sparks, oxygen starvation produces smoke over the fire, and
extinguishing produces a puff of smoke. Placing fuel is a right-click with logs, planks or sticks.
There is no interface and no fuel meter.

## Verification status

- Physics constants and the fire model are unit-tested (`src/test/java/...`): stoichiometry and
  mass conservation, the oxygen ceiling, extinction at the critical flux, air change behaviour,
  enclosure scanning, and fuel-bed geometry.
- Compilation and tests run in GitHub Actions (`.github/workflows/build.yml`), which is the only
  environment with a JVM for this repository.
- In-game behaviour (textures, particles, block state changes, persistence across save/load) has
  been reasoned through but not yet observed in a running client.

## Known limitations

- One well-mixed air zone per enclosure; no stratification, no thermal buoyancy of the smoke.
- No carbon monoxide, no smouldering phase, no fire spread to neighbouring blocks.
- Temperature and the char layer are not state: heat leaves the model as energy, it does not warm
  the room or preheat the fuel.
- Enclosures larger than 4096 passable cells are treated as open air.
