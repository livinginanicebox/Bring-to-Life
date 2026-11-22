package com.bringtolife.block;

import com.bringtolife.BringToLife;
import com.bringtolife.game.AirSpaces;
import com.bringtolife.game.Enclosure;
import com.bringtolife.game.EnclosureScanner;
import com.bringtolife.game.EnclosureView;
import com.bringtolife.game.WorldEnclosureView;
import com.bringtolife.sim.AirSpace;
import com.bringtolife.sim.Burn;
import com.bringtolife.sim.BurnResult;
import com.bringtolife.sim.FireBed;
import com.bringtolife.sim.Fuel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class HearthBlockEntity extends BlockEntity {

    private static final int STEP_TICKS = 10;
    private static final double STEP_SECONDS = STEP_TICKS / 20.0;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final double MAX_FUEL_KG = 40.0;
    private static final double HEAT_1_WATTS = 3_000.0;
    private static final double HEAT_2_WATTS = 15_000.0;
    private static final double HEAT_3_WATTS = 40_000.0;

    private final FireBed bed = new FireBed();
    private int ticksSinceScan;
    private int ticksSinceStep;
    private Enclosure enclosure;

    public HearthBlockEntity(BlockPos pos, BlockState state) {
        super(BringToLife.HEARTH_BLOCK_ENTITY, pos, state);
    }

    public void addFuel(Fuel fuel) {
        double room = MAX_FUEL_KG - bed.totalMassKg();
        if (room <= 0.0) {
            return;
        }
        double pieceKg = Math.min(pieceMassKg(fuel), room);
        bed.add(fuel, pieceKg);
        markDirty();
    }

    public double storedFuelKg() {
        return bed.totalMassKg();
    }

    private static double pieceMassKg(Fuel fuel) {
        if (fuel == Fuel.LOG) {
            return 5.0;
        }
        if (fuel == Fuel.PLANK) {
            return 1.0;
        }
        return 0.05;
    }

    public void tickServer(ServerWorld world, BlockPos pos, BlockState state) {
        ticksSinceScan++;
        if (enclosure == null || ticksSinceScan >= SCAN_INTERVAL_TICKS) {
            enclosure = scan(world, pos);
            ticksSinceScan = 0;
        }

        ticksSinceStep++;
        if (ticksSinceStep < STEP_TICKS) {
            return;
        }
        ticksSinceStep = 0;

        boolean lit = state.get(HearthBlock.LIT);
        boolean stillLit = lit;
        int heat = 0;
        boolean starved = false;
        boolean burnedSomething = false;

        if (lit && !bed.isEmpty()) {
            Fuel fuel = bed.dominantFuel();
            double area = bed.exposedAreaM2();
            double requestedKg = Math.min(bed.burningRateKgPerS() * STEP_SECONDS, bed.totalMassKg());
            if (enclosure.open()) {
                double burnedKg = bed.consume(requestedKg);
                burnedSomething = burnedKg > 0.0;
                heat = heatLevel(burnedKg * fuel.lowerHeatingValueJPerKg() / STEP_SECONDS);
            } else {
                AirSpaces spaces = AirSpaces.get(world);
                long key = keyOf(enclosure);
                AirSpace space = spaces.get(key, enclosure.volumeM3(), enclosure.airChangesPerHour())
                        .ventilate(STEP_SECONDS);
                BurnResult result = space.burn(fuel, requestedKg, area, STEP_SECONDS);
                Burn burn = result.burn();
                if (burn.extinguished()) {
                    stillLit = false;
                } else {
                    burnedSomething = bed.consume(burn.fuelKg()) > 0.0;
                    heat = heatLevel(burn.heatJ() / STEP_SECONDS);
                    starved = burn.limitedByOxygen();
                }
                spaces.put(key, result.space(), world.getTime());
            }
        }

        if (burnedSomething) {
            markDirty();
        }

        if (bed.isEmpty()) {
            heat = 0;
            stillLit = false;
        }

        if (stillLit != lit || state.get(HearthBlock.HEAT) != heat) {
            world.setBlockState(pos, state.with(HearthBlock.LIT, stillLit).with(HearthBlock.HEAT, heat), Block.NOTIFY_LISTENERS);
        }

        if (stillLit) {
            showFire(world, pos, heat, starved);
        } else if (lit) {
            puffOut(world, pos);
        }
    }

    private static int heatLevel(double watts) {
        if (watts >= HEAT_3_WATTS) {
            return 3;
        }
        if (watts >= HEAT_2_WATTS) {
            return 2;
        }
        if (watts >= HEAT_1_WATTS) {
            return 1;
        }
        return 0;
    }

    private static void showFire(ServerWorld world, BlockPos pos, int heat, boolean starved) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.02;
        double z = pos.getZ() + 0.5;
        if (starved) {
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 2, 0.25, 0.15, 0.25, 0.01);
            world.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0.15, 0.05, 0.15, 0.005);
            return;
        }
        switch (heat) {
            case 3 -> {
                world.spawnParticles(ParticleTypes.FLAME, x, y, z, 4, 0.3, 0.15, 0.3, 0.02);
                world.spawnParticles(ParticleTypes.SMALL_FLAME, x, y, z, 3, 0.3, 0.2, 0.3, 0.02);
                world.spawnParticles(ParticleTypes.LAVA, x, y + 0.1, z, 1, 0.25, 0.1, 0.25, 0.0);
            }
            case 2 -> {
                world.spawnParticles(ParticleTypes.FLAME, x, y, z, 2, 0.2, 0.1, 0.2, 0.01);
                world.spawnParticles(ParticleTypes.SMALL_FLAME, x, y, z, 1, 0.2, 0.1, 0.2, 0.01);
            }
            case 1 -> world.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0.12, 0.05, 0.12, 0.005);
            default -> {
                world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.1, 0.05, 0.1, 0.005);
                world.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0.1, 0.02, 0.1, 0.002);
            }
        }
    }

    private static void puffOut(ServerWorld world, BlockPos pos) {
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                6, 0.3, 0.2, 0.3, 0.02);
    }

    private static long keyOf(Enclosure enclosure) {
        return BlockPos.asLong(enclosure.minX(), enclosure.minY(), enclosure.minZ());
    }

    private static Enclosure scan(ServerWorld world, BlockPos pos) {
        EnclosureView view = new WorldEnclosureView(world);
        BlockPos origin = pos.up();
        if (!view.isPassable(origin.getX(), origin.getY(), origin.getZ())) {
            return new Enclosure(true, 0, 0.0, pos.getX(), pos.getY(), pos.getZ());
        }
        return EnclosureScanner.scan(view, origin.getX(), origin.getY(), origin.getZ());
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putDouble("LogKg", bed.massKg(Fuel.LOG));
        nbt.putDouble("PlankKg", bed.massKg(Fuel.PLANK));
        nbt.putDouble("StickKg", bed.massKg(Fuel.STICK));
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("LogKg")) {
            if (nbt.getDouble("LogKg") > 0.0) {
                bed.add(Fuel.LOG, nbt.getDouble("LogKg"));
            }
            if (nbt.getDouble("PlankKg") > 0.0) {
                bed.add(Fuel.PLANK, nbt.getDouble("PlankKg"));
            }
            if (nbt.getDouble("StickKg") > 0.0) {
                bed.add(Fuel.STICK, nbt.getDouble("StickKg"));
            }
        } else if (nbt.getDouble("FuelKg") > 0.0) {
            bed.add(Fuel.LOG, nbt.getDouble("FuelKg"));
        }
    }
}
