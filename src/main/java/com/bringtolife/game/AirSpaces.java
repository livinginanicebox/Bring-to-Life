package com.bringtolife.game;

import com.bringtolife.sim.AirSpace;
import com.bringtolife.sim.Gas;
import com.bringtolife.sim.GasMixture;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AirSpaces extends PersistentState {

    private static final String ID = "bring_to_life_air";
    private static final int MAX_TRACKED_SPACES = 512;
    private static final long STALE_TICKS = 6000L;

    private final Map<Long, AirSpace> spaces = new HashMap<>();
    private final Map<Long, Long> lastSeen = new HashMap<>();

    public static AirSpaces get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(AirSpaces::fromNbt, AirSpaces::new, ID);
    }

    public AirSpace get(long key, double volumeM3, double airChangesPerHour) {
        AirSpace space = spaces.get(key);
        if (space == null) {
            return AirSpace.sealed(volumeM3).withAirChanges(airChangesPerHour);
        }
        return space.resized(volumeM3).withAirChanges(airChangesPerHour);
    }

    public void put(long key, AirSpace space, long gameTime) {
        spaces.put(key, space);
        lastSeen.put(key, gameTime);
        prune(gameTime);
        markDirty();
    }

    private void prune(long gameTime) {
        if (spaces.size() <= MAX_TRACKED_SPACES) {
            return;
        }
        Iterator<Map.Entry<Long, Long>> iterator = lastSeen.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (gameTime - entry.getValue() > STALE_TICKS) {
                spaces.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        for (Map.Entry<Long, AirSpace> entry : spaces.entrySet()) {
            AirSpace space = entry.getValue();
            NbtCompound compound = new NbtCompound();
            compound.putDouble("volume", space.volumeM3());
            compound.putDouble("air_changes", space.airChangesPerHour());
            compound.putLong("seen", lastSeen.getOrDefault(entry.getKey(), 0L));
            NbtCompound gases = new NbtCompound();
            for (Map.Entry<Gas, Double> gas : space.mixture().amounts().entrySet()) {
                gases.putDouble(gas.getKey().name(), gas.getValue());
            }
            compound.put("gases", gases);
            nbt.put(Long.toString(entry.getKey()), compound);
        }
        return nbt;
    }

    public static AirSpaces fromNbt(NbtCompound nbt) {
        AirSpaces state = new AirSpaces();
        for (String key : nbt.getKeys()) {
            long packed = parseKey(key);
            if (packed == Long.MIN_VALUE) {
                continue;
            }
            NbtCompound compound = nbt.getCompound(key);
            NbtCompound gases = compound.getCompound("gases");
            EnumMap<Gas, Double> amounts = new EnumMap<>(Gas.class);
            for (Gas gas : Gas.values()) {
                if (gases.contains(gas.name())) {
                    amounts.put(gas, gases.getDouble(gas.name()));
                }
            }
            if (amounts.isEmpty()) {
                continue;
            }
            state.spaces.put(packed,
                    new AirSpace(compound.getDouble("volume"), new GasMixture(amounts), compound.getDouble("air_changes")));
            state.lastSeen.put(packed, compound.getLong("seen"));
        }
        return state;
    }

    private static long parseKey(String key) {
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException exception) {
            return Long.MIN_VALUE;
        }
    }
}
