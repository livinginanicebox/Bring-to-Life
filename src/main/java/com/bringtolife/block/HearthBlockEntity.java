package com.bringtolife.block;

import com.bringtolife.BringToLife;
import com.bringtolife.game.AirSpaces;
import com.bringtolife.game.Enclosure;
import com.bringtolife.game.EnclosureScanner;
import com.bringtolife.game.EnclosureView;
import com.bringtolife.game.WorldEnclosureView;
import com.bringtolife.sim.AirSpace;
import com.bringtolife.sim.BurnResult;
import com.bringtolife.sim.Fuel;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class HearthBlockEntity extends BlockEntity {

    private static final double TICK_SECONDS = 0.5;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final double MAX_FUEL_KG = 40.0;

    private double fuelKg;
    private int ticksSinceScan;
    private Enclosure enclosure;

    public HearthBlockEntity(BlockPos pos, BlockState state) {
        super(BringToLife.HEARTH_BLOCK_ENTITY, pos, state);
    }

    public void addFuel(double amountKg) {
        fuelKg = Math.min(MAX_FUEL_KG, fuelKg + amountKg);
        markDirty();
    }

    public double fuelKg() {
        return fuelKg;
    }

    public void tickServer(ServerWorld world, BlockPos pos, BlockState state) {
        ticksSinceScan++;
        if (enclosure == null || ticksSinceScan >= SCAN_INTERVAL_TICKS) {
            enclosure = scan(world, pos);
            ticksSinceScan = 0;
        }

        boolean lit = state.get(HearthBlock.LIT);
        boolean stillLit = lit;
        double freeRate = Fuel.DRY_WOOD.burningRateKgPerS(HearthBlock.EXPOSED_AREA_M2);
        double requestedKg = Math.min(fuelKg, freeRate * TICK_SECONDS);

        if (enclosure.open()) {
            if (lit && requestedKg > 0.0) {
                fuelKg -= requestedKg;
                spawnFlame(world, pos);
            }
        } else {
            AirSpaces spaces = AirSpaces.get(world);
            long key = keyOf(enclosure);
            AirSpace space = spaces.get(key, enclosure.volumeM3(), enclosure.airChangesPerHour()).ventilate(TICK_SECONDS);
            if (lit && requestedKg > 0.0) {
                BurnResult result = space.burn(Fuel.DRY_WOOD, requestedKg, HearthBlock.EXPOSED_AREA_M2, TICK_SECONDS);
                space = result.space();
                if (result.burn().extinguished()) {
                    stillLit = false;
                    spawnSmoke(world, pos);
                } else {
                    fuelKg -= result.burn().fuelKg();
                    spawnFlame(world, pos);
                    if (result.burn().limitedByOxygen()) {
                        spawnSmoke(world, pos);
                    }
                }
            }
            spaces.put(key, space, world.getTime());
        }

        if (fuelKg <= 0.0) {
            fuelKg = 0.0;
            stillLit = false;
        }
        if (stillLit != lit) {
            world.setBlockState(pos, state.with(HearthBlock.LIT, stillLit), net.minecraft.block.Block.NOTIFY_LISTENERS);
            markDirty();
        }
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

    private static void spawnFlame(ServerWorld world, BlockPos pos) {
        world.spawnParticles(ParticleTypes.FLAME,
                pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                1, 0.2, 0.1, 0.2, 0.01);
    }

    private static void spawnSmoke(ServerWorld world, BlockPos pos) {
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                1, 0.2, 0.2, 0.2, 0.01);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putDouble("FuelKg", fuelKg);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        fuelKg = nbt.getDouble("FuelKg");
    }
}
